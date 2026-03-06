package com.example.simpletool;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private FileAdapter fileAdapter;
    private List<Object> fileList;
    private TextView tvCurrentPath;
    private String currentPath;

    private static final int REQUEST_CODE_PERMISSIONS = 1001;
    private static final int REQUEST_CODE_MANAGE_STORAGE = 1002;

    // Android 10 及以下需要的权限
    private static final String[] REQUIRED_PERMISSIONS_LEGACY = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private static final String[] IMAGE_EXTENSIONS = {
            "jpg", "jpeg", "png", "gif", "bmp", "webp"
    };

    private enum SortMode {
        NAME_ASC, NAME_DESC,
        SIZE_ASC, SIZE_DESC,
        DATE_ASC, DATE_DESC
    }

    private SortMode currentSortMode = SortMode.NAME_ASC;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        checkPermissions();
    }

    private void initViews() {
        tvCurrentPath = findViewById(R.id.tv_current_path);
        recyclerView = findViewById(R.id.recyclerView);
        fileList = new ArrayList<>();
        fileAdapter = new FileAdapter(this, fileList);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(fileAdapter);

        findViewById(R.id.btn_sort).setOnClickListener(v -> showSortDialog());
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 需要 MANAGE_EXTERNAL_STORAGE 权限
            if (!Environment.isExternalStorageManager()) {
                requestManageExternalStoragePermission();
            } else {
                loadDirectory(getDownloadsDirectory().getAbsolutePath());
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 使用 scoped storage，但仍需尝试获取公共目录
            if (allPermissionsGranted()) {
                loadDirectory(getDownloadsDirectory().getAbsolutePath());
            } else {
                ActivityCompat.requestPermissions(
                        this,
                        REQUIRED_PERMISSIONS_LEGACY,
                        REQUEST_CODE_PERMISSIONS
                );
            }
        } else {
            // Android 9 及以下
            if (allPermissionsGranted()) {
                loadDirectory(getDownloadsDirectory().getAbsolutePath());
            } else {
                ActivityCompat.requestPermissions(
                        this,
                        REQUIRED_PERMISSIONS_LEGACY,
                        REQUEST_CODE_PERMISSIONS
                );
            }
        }
    }

    // 请求 MANAGE_EXTERNAL_STORAGE 权限（Android 11+）
    private void requestManageExternalStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_MANAGE_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    loadDirectory(getDownloadsDirectory().getAbsolutePath());
                } else {
                    Toast.makeText(this, "需要存储权限才能浏览文件", Toast.LENGTH_LONG).show();
                    // 降级到应用私有目录
                    loadDirectory(getAppPrivateDirectory().getAbsolutePath());
                }
            }
        }
    }

    // 获取 Downloads 目录 - 针对 Android 10+ 和 vivo 手机优化
    private File getDownloadsDirectory() {
        // 方法1：尝试标准 API
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (isValidDirectory(downloadDir)) {
            Log.d("FileExplorer", "使用标准API获取Downloads目录: " + downloadDir.getAbsolutePath());
            return downloadDir;
        }

        // 方法2：常见 Downloads 路径（适配 vivo、华为等国产手机）
        String[] commonPaths = {
                "/storage/emulated/0/Download",
                "/storage/emulated/0/Downloads",
                "/storage/emulated/0/download",
                "/storage/emulated/0/downloads",
                "/sdcard/Download",
                "/sdcard/Downloads",
                "/sdcard/download",
                "/sdcard/downloads"
        };

        for (String path : commonPaths) {
            File dir = new File(path);
            if (isValidDirectory(dir)) {
                Log.d("FileExplorer", "使用常见路径获取Downloads目录: " + path);
                return dir;
            }
        }

        // 方法3：vivo 手机特殊路径
        String[] vivoPaths = {
                "/storage/emulated/0/荣耀/Download",
                "/storage/emulated/0/荣耀/Downloads",
                "/storage/emulated/0/VIVO/Download",
                "/storage/emulated/0/VIVO/Downloads",
                "/storage/emulated/0/Android/data/com.android.providers.downloads.documents"
        };

        for (String path : vivoPaths) {
            File dir = new File(path);
            if (isValidDirectory(dir)) {
                Log.d("FileExplorer", "使用vivo特殊路径获取Downloads目录: " + path);
                return dir;
            }
        }

        // 方法4：获取外部存储根目录，让用户手动进入 Downloads
        File externalStorage = Environment.getExternalStorageDirectory();
        if (isValidDirectory(externalStorage)) {
            Log.d("FileExplorer", "使用外部存储根目录: " + externalStorage.getAbsolutePath());
            return externalStorage;
        }

        // 方法5：终极降级 - 应用私有目录
        File privateDir = getAppPrivateDirectory();
        Log.d("FileExplorer", "降级到应用私有目录: " + privateDir.getAbsolutePath());
        return privateDir;
    }

    // 检查目录是否有效（存在、可读、是目录）
    private boolean isValidDirectory(File dir) {
        return dir != null && dir.exists() && dir.isDirectory() && dir.canRead();
    }

    // 获取应用私有目录
    private File getAppPrivateDirectory() {
        File privateDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (privateDir != null && privateDir.exists()) {
            return privateDir;
        }
        // 备用：应用私有根目录
        return getExternalFilesDir(null);
    }

    private void loadDirectory(String path) {
        Log.d("FileExplorer", "Loading directory: " + path);

        if (path == null || path.isEmpty()) {
            loadDirectory(getDownloadsDirectory().getAbsolutePath());
            return;
        }

        currentPath = path;
        File currentDir = new File(path);
        fileList.clear();

        // 添加"返回上级"按钮
        if (shouldShowBackButton(currentDir)) {
            fileList.add(new BackItem());
        }

        File[] filesArray = currentDir.listFiles();
        if (filesArray != null) {
            List<File> allFiles = new ArrayList<>();
            for (File file : filesArray) {
                if (file != null) {
                    if ((file.isDirectory() && file.canRead()) || file.isFile()) {
                        allFiles.add(file);
                    }
                }
            }
            sortFiles(allFiles);
            fileList.addAll(allFiles);
        } else {
            Toast.makeText(this, "无法访问此目录（权限限制）", Toast.LENGTH_SHORT).show();
            loadDirectory(getDownloadsDirectory().getAbsolutePath());
            return;
        }

        updatePathDisplay(path);
        fileAdapter.notifyDataSetChanged();
    }

    // 智能判断是否显示"返回上级"按钮
    private boolean shouldShowBackButton(File currentDir) {
        if (currentDir == null) return false;

        // Android 10 禁止返回到根目录
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String parentPath = currentDir.getParent();
            if (parentPath != null) {
                if (parentPath.equals("/storage") ||
                        parentPath.equals("/mnt") ||
                        parentPath.equals("/")) {
                    return false;
                }
                File externalStorage = Environment.getExternalStorageDirectory();
                if (externalStorage != null && parentPath.equals(externalStorage.getAbsolutePath())) {
                    return true;
                }
            }
        }
        return currentDir.getParent() != null;
    }

    private void openFile(File file) {
        if (file == null || file.isDirectory()) return;

        try {
            if (!file.canRead()) {
                Toast.makeText(this, "文件不可读（权限限制）", Toast.LENGTH_SHORT).show();
                return;
            }

            if (isTextFile(file)) {
                openTextReader(file);
            } else if (isImageFile(file)) {
                openImageGallery(file);
            } else {
                openWithSystemApp(file);
            }
        } catch (Exception e) {
            handleFileAccessError(file, e);
        }
    }

    private void openImageGallery(File imageFile) {
        File parentDir = imageFile.getParentFile();
        if (parentDir == null || !parentDir.canRead()) {
            Toast.makeText(this, "无法访问图片所在目录", Toast.LENGTH_SHORT).show();
            return;
        }

        File[] allFiles = parentDir.listFiles();
        if (allFiles == null) {
            Toast.makeText(this, "目录为空", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<String> imagePaths = new ArrayList<>();
        int position = 0;

        List<File> imageFiles = Arrays.stream(allFiles)
                .filter(this::isImageFile)
                .sorted((f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()))
                .collect(Collectors.toList());

        for (int i = 0; i < imageFiles.size(); i++) {
            File f = imageFiles.get(i);
            imagePaths.add(f.getAbsolutePath());
            if (f.equals(imageFile)) {
                position = i;
            }
        }

        if (imagePaths.isEmpty()) {
            Toast.makeText(this, "该目录下没有图片", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, ImageListActivity.class);
        intent.putStringArrayListExtra("image_paths", imagePaths);
        intent.putExtra("position", position);
        startActivity(intent);
    }

    private boolean isTextFile(File file) {
        if (file == null) return false;
        String name = file.getName().toLowerCase();
        return name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".md") || name.endsWith(".json");
    }

    private void openTextReader(File file) {
        if (file == null || !file.exists() || file.length() == 0) {
            Toast.makeText(this, "无效的文本文件", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Intent intent = new Intent(this, NovelReaderActivity.class);
            intent.putExtra("file_path", file.getAbsolutePath());
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "未找到文本阅读器", Toast.LENGTH_SHORT).show();
        }
    }

    private void openWithSystemApp(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    file
            );

            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, getMimeType(file))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, "没有应用可以打开此文件", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            handleFileAccessError(file, e);
        }
    }

    private String getMimeType(File file) {
        if (file == null) return "*/*";

        String extension = MimeTypeMap.getFileExtensionFromUrl(file.getName());
        if (extension != null) {
            String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
            if (mimeType != null) return mimeType;
        }

        try (InputStream is = new FileInputStream(file)) {
            String mimeType = URLConnection.guessContentTypeFromStream(is);
            if (mimeType != null && !mimeType.isEmpty()) return mimeType;
        } catch (IOException ignored) {
        }

        return "*/*";
    }

    private void handleFileAccessError(File file, Exception e) {
        Log.e("FileAccess", "Error accessing: " + (file != null ? file.getAbsolutePath() : "null"), e);

        String errorMsg = "无法打开文件";
        if (e instanceof SecurityException) {
            errorMsg = "权限不足，无法访问此文件";
        } else if (file != null && !file.canRead()) {
            errorMsg = "文件不可读（可能被其他应用占用）";
        }

        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
    }

    public boolean isImageFile(File file) {
        if (file == null || !file.isFile()) return false;

        String name = file.getName().toLowerCase();
        for (String ext : IMAGE_EXTENSIONS) {
            if (name.endsWith("." + ext)) return true;
        }
        return false;
    }

    private void updatePathDisplay(String path) {
        if (path == null || path.isEmpty()) {
            tvCurrentPath.setText("文件浏览器");
            return;
        }

        String displayPath = path;
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (downloadDir != null && path.startsWith(downloadDir.getAbsolutePath())) {
            displayPath = "Download" + path.substring(downloadDir.getAbsolutePath().length());
        } else {
            File externalStorage = Environment.getExternalStorageDirectory();
            if (externalStorage != null) {
                displayPath = path.replace(externalStorage.getAbsolutePath(), "内部存储");
            }
        }

        if (displayPath.length() > 40) {
            displayPath = "..." + displayPath.substring(displayPath.length() - 37);
        }

        tvCurrentPath.setText(displayPath);
    }

    @Override
    public void onBackPressed() {
        if (currentPath == null) {
            super.onBackPressed();
            return;
        }

        File currentDir = new File(currentPath);
        String parentPath = currentDir.getParent();

        // Android 10+ 禁止返回到危险根目录
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && parentPath != null) {
            if (parentPath.equals("/storage") ||
                    parentPath.equals("/mnt") ||
                    parentPath.equals("/")) {
                loadDirectory(getDownloadsDirectory().getAbsolutePath());
                return;
            }

            File externalStorage = Environment.getExternalStorageDirectory();
            if (externalStorage != null && parentPath.equals(externalStorage.getAbsolutePath())) {
                loadDirectory(parentPath);
                return;
            }
        }

        if (parentPath != null) {
            loadDirectory(parentPath);
        } else {
            super.onBackPressed();
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS_LEGACY) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public void navigateTo(File target) {
        if (target == null) return;

        if (target.isDirectory()) {
            // Android 10 阻止访问危险目录
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                String path = target.getAbsolutePath();
                if (path.startsWith("/storage") && !path.startsWith(Environment.getExternalStorageDirectory().getAbsolutePath())) {
                    Toast.makeText(this, "Android 10 限制：无法访问此目录", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            loadDirectory(target.getAbsolutePath());
        } else {
            openFile(target);
        }
    }

    private void sortFiles(List<File> files) {
        if (files == null || files.isEmpty()) return;

        Collections.sort(files, (f1, f2) -> {
            if (f1.isDirectory() && !f2.isDirectory()) return -1;
            if (!f1.isDirectory() && f2.isDirectory()) return 1;

            switch (currentSortMode) {
                case NAME_ASC:
                    return f1.getName().compareToIgnoreCase(f2.getName());
                case NAME_DESC:
                    return f2.getName().compareToIgnoreCase(f1.getName());
                case SIZE_ASC:
                    return Long.compare(f1.length(), f2.length());
                case SIZE_DESC:
                    return Long.compare(f2.length(), f1.length());
                case DATE_ASC:
                    return Long.compare(f1.lastModified(), f2.lastModified());
                case DATE_DESC:
                    return Long.compare(f2.lastModified(), f1.lastModified());
                default:
                    return 0;
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_sort) {
            showSortDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSortDialog() {
        String[] sortOptions = {"名称↑", "名称↓", "大小↑", "大小↓", "时间↑", "时间↓"};
        new AlertDialog.Builder(this)
                .setTitle("排序方式")
                .setItems(sortOptions, (dialog, which) -> {
                    currentSortMode = SortMode.values()[which];
                    if (currentPath != null) {
                        loadDirectory(currentPath);
                    }
                })
                .show();
    }

    private void showDeleteDialog(File file) {
        if (file == null) return;

        new AlertDialog.Builder(this)
                .setTitle("删除确认")
                .setMessage("确定要删除 \"" + file.getName() + "\" 吗？\n\n此操作不可恢复！")
                .setPositiveButton("删除", (dialog, which) -> deleteFile(file))
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteFile(File file) {
        if (file == null) return;

        boolean success = file.delete();
        String msg = success ? "删除成功" : "删除失败（权限不足）";
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

        if (success && currentPath != null) {
            loadDirectory(currentPath);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                loadDirectory(getDownloadsDirectory().getAbsolutePath());
            } else {
                Toast.makeText(this, "需要存储权限才能浏览文件", Toast.LENGTH_SHORT).show();
                // 降级到应用私有目录
                loadDirectory(getAppPrivateDirectory().getAbsolutePath());
                Toast.makeText(this, "已切换到应用私有目录", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 返回上级项
    private static class BackItem extends File {
        BackItem() {
            super("..");
        }
    }

    // 适配器实现
    private static class FileAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_BACK = 0;
        private static final int TYPE_FILE = 1;

        private final Context context;
        private final List<Object> items;

        FileAdapter(Context context, List<Object> items) {
            this.context = context;
            this.items = items;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(context);
            View view = inflater.inflate(R.layout.item_file, parent, false);
            return new FileHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object item = items.get(position);
            FileHolder fh = (FileHolder) holder;

            if (item instanceof BackItem) {
                fh.icon.setText("⬅️");
                fh.name.setText("返回上级");
                fh.itemView.setOnClickListener(v -> {
                    MainActivity activity = (MainActivity) context;
                    if (activity.currentPath != null) {
                        File currentDir = new File(activity.currentPath);
                        String parent = currentDir.getParent();
                        if (parent != null) {
                            activity.loadDirectory(parent);
                        }
                    }
                });
            } else if (item instanceof File) {
                File file = (File) item;
                fh.icon.setText(file.isDirectory() ? "📁" : getIconForFile(file));
                fh.name.setText(file.getName());

                // 长按删除（仅文件）
                fh.itemView.setOnLongClickListener(v -> {
                    if (!file.isDirectory()) {
                        ((MainActivity) context).showDeleteDialog(file);
                        return true;
                    }
                    return false;
                });

                // 点击导航
                fh.itemView.setOnClickListener(v -> ((MainActivity) context).navigateTo(file));
            }
        }

        private String getIconForFile(File file) {
            String name = file.getName().toLowerCase();
            if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
                    name.endsWith(".gif") || name.endsWith(".bmp") || name.endsWith(".webp")) {
                return "🖼️";
            } else if (name.endsWith(".mp4") || name.endsWith(".avi") || name.endsWith(".mkv")) {
                return "🎬";
            } else if (name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".flac")) {
                return "🎵";
            } else if (name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".md")) {
                return "📄";
            } else if (name.endsWith(".pdf")) {
                return "📕";
            } else if (name.endsWith(".apk")) {
                return "📱";
            }
            return "📎";
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position) instanceof BackItem ? TYPE_BACK : TYPE_FILE;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class FileHolder extends RecyclerView.ViewHolder {
            TextView icon, name;

            FileHolder(View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.file_icon);
                name = itemView.findViewById(R.id.file_name);
            }
        }
    }
}
