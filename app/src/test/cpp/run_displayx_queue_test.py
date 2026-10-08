#!/usr/bin/env python3
"""Exercise the actual DisplayX queue without Android headers or a device."""
import os
from pathlib import Path
import subprocess
import tempfile

source = (Path(__file__).resolve().parents[2] / 'main/cpp/winlator/renderer/displayx.hpp').read_text()
queue = source[source.index('        class PresentQueue {'):source.index('        struct DisplayXSwapchain {')]
harness = r'''
#include <algorithm>
#include <cassert>
#include <deque>
#include <fcntl.h>
#include <memory>
#include <unordered_map>
#include <utility>
#include <vector>
#include <unistd.h>
struct Window {};
struct PresentRequest { Window *window; int sync_fence; int id; };
'''
checks = r'''
int main() {
    Window a, b;
    PresentQueue queue;
    auto push = [&](Window *window, int id, bool mailbox, int fd = -1) {
        queue.push(std::make_unique<PresentRequest>(PresentRequest{window, fd, id}), mailbox);
    };
    int dropped = dup(STDIN_FILENO);
    assert(dropped >= 0);
    push(&a, 1, true, dropped);
    push(&a, 2, true);
    assert(fcntl(dropped, F_GETFD) == -1);
    auto latest = queue.getLast();
    assert(latest.size() == 1 && latest.front()->id == 2 && queue.empty());
    push(&a, 3, false);
    push(&a, 4, false);
    auto fifo = queue.buildWindowTree();
    assert(fifo.size() == 2 && fifo.front()->id == 3 && fifo.back()->id == 4 && queue.empty());
    int removed = dup(STDIN_FILENO);
    push(&a, 5, true, removed);
    push(&b, 6, true);
    queue.removeWindow(&a);
    assert(fcntl(removed, F_GETFD) == -1);
    auto remaining = queue.getLast();
    assert(remaining.size() == 1 && remaining.front()->window == &b && queue.empty());
    queue.removeWindow(nullptr);
    push(&a, 7, true);
    push(&b, 8, true);
    push(&a, 9, true);
    latest = queue.getLast();
    assert(latest.size() == 1 && latest.front()->id == 9);
    remaining = queue.getLast();
    assert(remaining.size() == 1 && remaining.front()->id == 8 && queue.empty());
}
'''
with tempfile.TemporaryDirectory(prefix='displayx-queue-') as directory:
    temp = Path(directory)
    (temp / 'test.cpp').write_text(harness + queue + checks)
    subprocess.run([os.environ.get('CXX', 'c++'), '-std=c++17', str(temp / 'test.cpp'), '-o', str(temp / 'test')], check=True)
    subprocess.run([str(temp / 'test')], check=True)
print('DisplayX mailbox, FIFO, window removal, and fence checks passed')
