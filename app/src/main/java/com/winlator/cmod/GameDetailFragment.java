package com.winlator.cmod;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.WineInfo;
import com.winlator.cmod.ui.library.GameDetailCallbacks;
import com.winlator.cmod.ui.library.GameDetailComposeHost;
import com.winlator.cmod.ui.library.GameSavesComposeDialog;
import com.winlator.cmod.ui.shortcut.ShortcutSettingsComposeDialog;
import com.winlator.cmod.util.GameRestorePackageManager;

import org.json.JSONObject;

import java.io.File;

public class GameDetailFragment extends Fragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    private final String shortcutPath;
    private Shortcut shortcut;
    private ProgressDialog exportProgressDialog;

    public GameDetailFragment() {
        this("");
    }

    public GameDetailFragment(String shortcutPath) {
        this.shortcutPath = shortcutPath;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        ContainerManager manager = new ContainerManager(requireContext());
        for (Shortcut candidate : manager.loadShortcuts()) {
            if (candidate != null && candidate.file != null
                    && candidate.file.getPath().equals(shortcutPath)) {
                shortcut = candidate;
                break;
            }
        }

        if (shortcut == null) {
            getParentFragmentManager().popBackStack();
            return new FrameLayout(requireContext());
        }

        ((AppCompatActivity) requireActivity()).getSupportActionBar().setTitle(shortcut.name);
        String baseName = FileUtils.getBasename(shortcut.file.getPath());
        File userArtwork = new File(Environment.getExternalStorageDirectory(),
                "Winlator/icons/" + baseName + ".user.png");
        File banner = new File(Environment.getExternalStorageDirectory(),
                "Winlator/banners/" + baseName + ".png");
        File cover = new File(Environment.getExternalStorageDirectory(),
                "Winlator/covers/" + baseName + ".png");
        String artworkPath = userArtwork.isFile() ? userArtwork.getPath()
                : banner.isFile() ? banner.getPath()
                : cover.isFile() ? cover.getPath() : null;
        Bitmap fallback = shortcut.icon;

        View content = GameDetailComposeHost.create(
                requireContext(),
                shortcut.name,
                buildEnvironmentSubtitle(),
                artworkPath,
                fallback,
                "1".equals(shortcut.getExtra("favorite", "0")),
                new GameDetailCallbacks() {
                    @Override
                    public void onPlay() {
                        runShortcut();
                    }

                    @Override
                    public void onConfigure() {
                        ShortcutSettingsComposeDialog.show(GameDetailFragment.this, shortcut);
                    }

                    @Override
                    public void onArguments() {
                        runContainer();
                    }

                    @Override
                    public void onSaves() {
                        GameSavesComposeDialog.show(GameDetailFragment.this, shortcut);
                    }

                    @Override
                    public void onFavorite(boolean favorite) {
                        shortcut.putExtra("favorite", favorite ? "1" : "0");
                        shortcut.saveData();
                    }

                    @Override
                    public void onRemove() {
                        ContentDialog.confirm(requireContext(), R.string.do_you_want_to_remove_this_shortcut, () -> {
                            if (shortcut.file.delete()) getParentFragmentManager().popBackStack();
                        });
                    }

                    @Override
                    public void onDownloadCover() {
                        downloadCoverManually();
                    }

                    @Override
                    public void onClearCover() {
                        clearCover();
                    }
                }
        );
        content.post(this::applyDetailChrome);
        return content;
    }

    private String buildEnvironmentSubtitle() {
        String runtime = shortcut.container.getWineVersion();
        try {
            ContentsManager contents = new ContentsManager(requireContext());
            contents.syncContents();
            WineInfo info = WineInfo.fromIdentifier(requireContext(), contents, runtime);
            String version = info.fullVersion();
            if (version.endsWith(".0")) version = version.substring(0, version.length() - 2);
            runtime = ("proton".equalsIgnoreCase(info.type) ? "Proton " : "Wine ") + version + " " + info.getArch();
        } catch (Exception ignored) {}
        String renderer = shortcut.getUseDisplayX() ? "DisplayX" : shortcut.getRendererNative() ? "EGL" : "Vulkan";
        return runtime + "  •  " + renderer;
    }

    private void downloadCoverManually() {
        String baseName = FileUtils.getBasename(shortcut.file.getPath());
        File cover = new File(Environment.getExternalStorageDirectory(), "Winlator/covers/" + baseName + ".png");
        // 复用ShortcutsFragment的SteamGridDB下载逻辑
        android.widget.Toast.makeText(requireContext(), "Searching cover on SteamGridDB...", android.widget.Toast.LENGTH_SHORT).show();
        // 直接调用SteamGridDB API搜索并下载第一个结果
        String apiKey = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString("steamgrid_api_key", "0324c52513634547a7b32d6d323635d0");
        retrofit2.Retrofit retrofit = new retrofit2.Retrofit.Builder()
                .baseUrl("https://www.steamgriddb.com/api/v2/")
                .client(new okhttp3.OkHttpClient())
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build();
        com.winlator.cmod.bigpicture.steamgrid.SteamGridDBApi api = retrofit.create(com.winlator.cmod.bigpicture.steamgrid.SteamGridDBApi.class);
        api.searchGame("Bearer " + apiKey, shortcut.name).enqueue(new retrofit2.Callback<com.winlator.cmod.bigpicture.steamgrid.SteamGridSearchResponse>() {
            @Override
            public void onResponse(retrofit2.Call<com.winlator.cmod.bigpicture.steamgrid.SteamGridSearchResponse> call, retrofit2.Response<com.winlator.cmod.bigpicture.steamgrid.SteamGridSearchResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().data != null && !response.body().data.isEmpty()) {
                    int gameId = response.body().data.get(0).id;
                    api.getGridsByGameId("Bearer " + apiKey, gameId, "alternate", "600x900", "static").enqueue(new retrofit2.Callback<com.winlator.cmod.bigpicture.steamgrid.SteamGridGridsResponse>() {
                        @Override
                        public void onResponse(retrofit2.Call<com.winlator.cmod.bigpicture.steamgrid.SteamGridGridsResponse> call, retrofit2.Response<com.winlator.cmod.bigpicture.steamgrid.SteamGridGridsResponse> response) {
                            if (response.isSuccessful() && response.body() != null && response.body().data != null && !response.body().data.isEmpty()) {
                                String url = response.body().data.get(0).url;
                                new Thread(() -> {
                                    try {
                                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                                        conn.connect();
                                        android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(conn.getInputStream());
                                        if (bmp != null) {
                                            if (cover.getParentFile() != null) cover.getParentFile().mkdirs();
                                            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(cover)) {
                                                bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos);
                                            }
                                            requireActivity().runOnUiThread(() -> {
                                                android.widget.Toast.makeText(requireContext(), "Cover downloaded", android.widget.Toast.LENGTH_SHORT).show();
                                                requireFragmentManager().beginTransaction().detach(GameDetailFragment.this).attach(GameDetailFragment.this).commit();
                                            });
                                        }
                                    } catch (Exception e) { e.printStackTrace(); }
                                }).start();
                            }
                        }
                        @Override
                        public void onFailure(retrofit2.Call<com.winlator.cmod.bigpicture.steamgrid.SteamGridGridsResponse> call, Throwable t) {}
                    });
                } else {
                    android.widget.Toast.makeText(requireContext(), "No cover found", android.widget.Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(retrofit2.Call<com.winlator.cmod.bigpicture.steamgrid.SteamGridSearchResponse> call, Throwable t) {
                android.widget.Toast.makeText(requireContext(), "Search failed", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void clearCover() {
        String baseName = FileUtils.getBasename(shortcut.file.getPath());
        File cover = new File(Environment.getExternalStorageDirectory(), "Winlator/covers/" + baseName + ".png");
        File banner = new File(Environment.getExternalStorageDirectory(), "Winlator/banners/" + baseName + ".png");
        boolean deleted = false;
        if (cover.exists()) { cover.delete(); deleted = true; }
        if (banner.exists()) { banner.delete(); deleted = true; }
        if (deleted) {
            android.widget.Toast.makeText(requireContext(), "Cover cleared", android.widget.Toast.LENGTH_SHORT).show();
            requireFragmentManager().beginTransaction().detach(this).attach(this).commit();
        } else {
            android.widget.Toast.makeText(requireContext(), "No cover to clear", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void runShortcut() {
        Activity activity = requireActivity();
        if (!XrActivity.isEnabled(requireContext())) {
            Intent intent = new Intent(activity, XServerDisplayActivity.class);
            intent.putExtra("container_id", shortcut.container.id);
            intent.putExtra("shortcut_path", shortcut.file.getPath());
            intent.putExtra("shortcut_name", shortcut.name);
            intent.putExtra("disableXinput", shortcut.getExtra("disableXinput", "0"));
            intent.putExtra("native_rendering", shortcut.getRendererNative());
            activity.startActivity(intent);
        } else {
            XrActivity.openIntent(activity, shortcut.container.id, shortcut.file.getPath());
        }
    }

    private void runContainer() {
        Activity activity = requireActivity();
        if (!XrActivity.isEnabled(requireContext())) {
            Intent intent = new Intent(activity, XServerDisplayActivity.class);
            intent.putExtra("container_id", shortcut.container.id);
            activity.startActivity(intent);
        } else {
            XrActivity.openIntent(activity, shortcut.container.id, null);
        }
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void applyDetailChrome() {
        if (!(getActivity() instanceof MainActivity)) return;
        MainActivity activity = (MainActivity) getActivity();
        activity.setDetailMode(true);
        if (isLandscape()) {
            activity.setBottomNavigationVisible(false);
            activity.setMainToolbarVisible(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        applyDetailChrome();
    }

    @Override
    public void onPause() {
        if (!isLandscape() && getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setDetailMode(false);
        }
        super.onPause();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.add(0, 3001, 0, "导出配置分享");
        menu.add(0, 3002, 1, "导出完整数据包");
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 3001) {
            exportConfigOnly();
            return true;
        } else if (item.getItemId() == 3002) {
            showExportDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * v8：导出确认窗口（两类语义：配置分享 / 完整数据包）
     * 展示：游戏信息卡 + 容器配置差异 + 快捷方式独立配置 + 导出类型单选
     * 配置分享(.json)：容器配置+快捷方式配置+组件清单，体积最小，适合分享
     * 完整数据包(.grp.zip)：配置+游戏文件，可选包含Wine运行环境，可整包迁移
     */
    private void showExportDialog() {
        final Context context = getContext();
        if (context == null || shortcut == null || shortcut.container == null) return;

        // 收集游戏信息（隐私保护：只取文件名和大小，不取路径）
        File gameExe = GameRestorePackageManager.getGameExeFile(shortcut);
        String exeFileName = (gameExe != null) ? gameExe.getName() : "（未找到exe文件）";
        long gameSize = GameRestorePackageManager.getGameDirectorySize(shortcut);
        String gameSizeStr = (gameSize > 0) ? formatSizeMB(gameSize) : "（未找到游戏目录）";
        long wineSize = GameRestorePackageManager.getWineRuntimeSize(shortcut);
        String wineSizeStr = (wineSize > 0) ? formatSizeMB(wineSize) : "（未知）";

        // 容器配置差异 + 快捷方式独立配置
        String configDiff = GameRestorePackageManager.getConfigDiffSummary(shortcut.container);
        String shortcutDiff = GameRestorePackageManager.getShortcutConfigDiff(shortcut);

        // 构建信息文本
        StringBuilder info = new StringBuilder();
        info.append("━━━ 游戏信息 ━━━\n");
        info.append("游戏名称: ").append(shortcut.name).append("\n");
        info.append("exe文件: ").append(exeFileName).append("\n");
        info.append("游戏目录大小: ").append(gameSizeStr).append("\n");
        info.append("Wine运行环境大小: ").append(wineSizeStr).append("\n");
        info.append("关联容器: ").append(shortcut.container.getName())
            .append("（ID: ").append(shortcut.container.id).append("）\n");

        info.append("\n━━━ 容器配置（与默认值对比）━━━\n");
        info.append(configDiff);

        info.append("\n━━━ 快捷方式独立配置 ━━━\n");
        info.append(shortcutDiff);

        // 自定义View：ScrollView + TextView + RadioGroup
        android.widget.ScrollView scrollView = new android.widget.ScrollView(context);
        android.widget.LinearLayout layout = new android.widget.LinearLayout(context);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (20 * context.getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        android.widget.TextView infoText = new android.widget.TextView(context);
        infoText.setText(info.toString());
        infoText.setTextSize(13);
        layout.addView(infoText);

        android.widget.TextView typeLabel = new android.widget.TextView(context);
        typeLabel.setText("\n请选择导出类型：");
        typeLabel.setTextSize(14);
        typeLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(typeLabel);

        final android.widget.RadioGroup radioGroup = new android.widget.RadioGroup(context);
        radioGroup.setOrientation(android.widget.LinearLayout.VERTICAL);

        android.widget.RadioButton rb1 = new android.widget.RadioButton(context);
        rb1.setText("配置分享(.json) — 容器配置+快捷方式+组件清单，体积最小，适合分享");
        rb1.setId(android.view.View.generateViewId());

        android.widget.RadioButton rb2 = new android.widget.RadioButton(context);
        rb2.setText("完整数据包(.grp.zip) — 配置+游戏文件，可整包迁移到其他设备");
        rb2.setId(android.view.View.generateViewId());

        // Wine运行环境勾选框：仅选数据包时可用
        final android.widget.CheckBox cbWineRuntime = new android.widget.CheckBox(context);
        cbWineRuntime.setText("包含Wine运行环境（可任意设备还原，体积约" + wineSizeStr + "）");
        cbWineRuntime.setEnabled(false);

        radioGroup.addView(rb1);
        radioGroup.addView(rb2);
        radioGroup.addView(cbWineRuntime);
        radioGroup.check(rb1.getId()); // 默认选中配置分享
        radioGroup.setOnCheckedChangeListener((group, checkedId) ->
                cbWineRuntime.setEnabled(checkedId == rb2.getId()));
        layout.addView(radioGroup);

        scrollView.addView(layout);

        new AlertDialog.Builder(context)
                .setTitle("导出")
                .setView(scrollView)
                .setPositiveButton("确认导出", (dialog, which) -> {
                    int checkedId = radioGroup.getCheckedRadioButtonId();
                    if (checkedId == rb1.getId()) {
                        // 配置分享：导出新格式JSON
                        exportConfigOnly();
                    } else {
                        // 完整数据包
                        boolean includeWineRuntime = cbWineRuntime.isChecked();
                        doExport(true, true, includeWineRuntime);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String formatSizeMB(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * 仅配置导出：新格式JSON（meta + container_config + shortcut_config + components）
     */
    private void exportConfigOnly() {
        final Context context = getContext();
        if (context == null || shortcut == null || shortcut.container == null) return;

        new Thread(() -> {
            try {
                org.json.JSONObject config = com.winlator.cmod.util.GameConfigSerializer
                        .exportConfig(context, shortcut, shortcut.container);
                String path = com.winlator.cmod.util.GameConfigSerializer
                        .saveConfigToFile(context, config, shortcut.name);

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            android.widget.Toast.makeText(context,
                                    "配置已导出: " + path, android.widget.Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            android.widget.Toast.makeText(context,
                                    "导出失败: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show());
                }
            }
        }).start();
    }

    /**
     * v5：导出时显示ProgressDialog，onProgress实时更新，支持Wine运行环境打包
     */
    private void doExport(boolean includeGameFiles, boolean includeRegistry, boolean includeWineRuntime) {
        final Context context = getContext();
        if (context == null || shortcut == null) return;

        exportProgressDialog = new ProgressDialog(context);
        exportProgressDialog.setTitle("正在导出游戏数据包");
        exportProgressDialog.setMessage("准备中...");
        exportProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        exportProgressDialog.setMax(100);
        exportProgressDialog.setCancelable(false);
        exportProgressDialog.show();

        GameRestorePackageManager.exportPackageAsync(context, shortcut,
                includeGameFiles, includeRegistry, includeWineRuntime, "", "",
                new GameRestorePackageManager.ExportCallback() {
                    @Override
                    public void onProgress(int percent, String message) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (exportProgressDialog != null && exportProgressDialog.isShowing()) {
                                    exportProgressDialog.setProgress(percent);
                                    exportProgressDialog.setMessage(message);
                                }
                            });
                        }
                    }

                    @Override
                    public void onComplete(String packagePath) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (exportProgressDialog != null && exportProgressDialog.isShowing()) {
                                    exportProgressDialog.dismiss();
                                }
                                Toast.makeText(context, "导出完成: " + packagePath, Toast.LENGTH_LONG).show();
                            });
                        }
                    }

                    @Override
                    public void onError(String error) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (exportProgressDialog != null && exportProgressDialog.isShowing()) {
                                    exportProgressDialog.dismiss();
                                }
                                Toast.makeText(context, "导出失败: " + error, Toast.LENGTH_LONG).show();
                            });
                        }
                    }
                });
    }

}
