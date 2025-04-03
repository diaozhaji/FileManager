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
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
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
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.READ_EXTERNAL_STORAGE
    };

    private static final String[] IMAGE_EXTENSIONS = {
            "jpg", "jpeg", "png", "gif", "bmp", "webp" // 基础图片格式
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        checkPermissions();
        requestStorageManagerPermission();
    }

    private void initViews() {
        tvCurrentPath = findViewById(R.id.tv_current_path);
        recyclerView = findViewById(R.id.recyclerView);
        fileList = new ArrayList<>();
        fileAdapter = new FileAdapter(this, fileList);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(fileAdapter);
    }

    private void checkPermissions() {
        if (allPermissionsGranted()) {
            showStorageRoots();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    REQUIRED_PERMISSIONS,
                    REQUEST_CODE_PERMISSIONS
            );
        }
    }

    private void requestStorageManagerPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                !Environment.isExternalStorageManager()) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
            );
            startActivity(intent);
        }
    }

    private void showStorageRoots() {
        fileList.clear();
        currentPath = null;

        // 添加内置存储
        fileList.add(new StorageVolumeItem(
                Environment.getExternalStorageDirectory(),
                "内部存储",
                false
        ));

        // 添加外置存储
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            StorageManager sm = getSystemService(StorageManager.class);
            for (StorageVolume volume : sm.getStorageVolumes()) {
                if (volume.isRemovable()) {
                    File volumePath = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        volumePath = volume.getDirectory();
                    }
                    fileList.add(new StorageVolumeItem(
                            volumePath,
                            "SD卡 - " + volume.getDescription(this),
                            true
                    ));
                }
            }
        }

        updatePathDisplay("存储设备");
        fileAdapter.notifyDataSetChanged();
    }

    private void loadDirectory(String path) {
        currentPath = path;
        File currentDir = new File(path);
        fileList.clear();

        // 添加返回上级（保持原有逻辑）
        if (!path.equals(getParentStoragePath())) {
            fileList.add(new BackItem());
        }

        File[] filesArray = currentDir.listFiles();
        if (filesArray != null) {
            List<File> directories = new ArrayList<>();
            List<File> fileItems = new ArrayList<>();

            // 分离目录和文件
            for (File file : filesArray) {
                if (file.isDirectory() && file.canRead()) {
                    directories.add(file);
                }
//                else if (isSupportedFile(file)) {
//                    fileItems.add(file);
//                }
                else {
                    fileItems.add(file);
                }
            }

            // 分别排序
            sortFiles(directories);
            sortFiles(fileItems);

            // 先添加目录，后添加文件
            fileList.addAll(directories);
            fileList.addAll(fileItems);
        }

        updatePathDisplay(path);
        fileAdapter.notifyDataSetChanged();
    }

    private String getParentStoragePath() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ?
                Environment.getStorageDirectory().getAbsolutePath() :
                "/storage";
    }

    private void openFile(File file) {
        // 如果是目录则直接返回
        if (file.isDirectory()) return;

        try {
            // 检查文件可读性
            if (!file.canRead()) {
                Toast.makeText(this, "文件不可读", Toast.LENGTH_SHORT).show();
                return;
            }

            // 根据文件类型选择打开方式
            if (isTextFile(file)) {
                openTextReader(file);
            } else if (isImageFile(file)) {
                openImageGallery(file);
            } else {
                openWithSystemApp(file);
            }
        } catch (SecurityException e) {
            handleFileAccessError(file, e);
        }
    }

    private void openImageGallery(File imageFile) {
        File parentDir = imageFile.getParentFile();
        File[] allFiles = parentDir.listFiles();

        ArrayList<String> imagePaths = new ArrayList<>();
        int position = 0;

        // 过滤并排序图片文件
        List<File> imageFiles = Arrays.stream(allFiles)
                .filter(this::isImageFile)
                .sorted((f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()))
                .collect(Collectors.toList());

        // 收集路径并找到当前图片位置
        for (int i = 0; i < imageFiles.size(); i++) {
            File f = imageFiles.get(i);
            imagePaths.add(f.getAbsolutePath());
            if (f.equals(imageFile)) {
                position = i;
            }
        }

        // 启动预览Activity
        Intent intent = new Intent(this, ImageListActivity.class);
        intent.putStringArrayListExtra("image_paths", imagePaths);
        intent.putExtra("position", position);
        startActivity(intent);
    }

    // 文本文件检测方法
    private boolean isTextFile(File file) {
        String fileName = file.getName().toLowerCase();
        return fileName.endsWith(".txt");
    }

    // 打开文本阅读器
    private void openTextReader(File file) {
        // 有效性验证
        if (!file.exists() || file.length() == 0) {
            Toast.makeText(this, "无效的文本文件", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Intent intent = new Intent(this, NovelReaderActivity.class);
            // 传递文件绝对路径
            intent.putExtra("file_path", file.getAbsolutePath());
            // 添加阅读器标志
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "无法启动阅读器", Toast.LENGTH_SHORT).show();
        }
    }

    // 系统应用打开方法（原有逻辑）
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
                Toast.makeText(this, "没有可用的应用", Toast.LENGTH_SHORT).show();
            }
        } catch (IllegalArgumentException e) {
            handleFileAccessError(file, e);
        }
    }


    // 改进的MIME类型检测方法
    private String getMimeType(File file) {
        // 1. 通过文件扩展名检测
        String extension = MimeTypeMap.getFileExtensionFromUrl(file.getName());
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());

        // 2. 通过ContentResolver检测（更准确）
        if (mimeType == null || mimeType.isEmpty()) {
            try (InputStream is = new FileInputStream(file)) {
                mimeType = URLConnection.guessContentTypeFromStream(is);
            } catch (IOException ignored) {
            }
        }

        // 3. 最终回退方案
        if (mimeType == null || mimeType.isEmpty()) {
            mimeType = "*/*"; // 通用类型
        }

        return mimeType;
    }

    private void handleFileAccessError(File file, Exception e) {
        Log.e("FileAccess", "文件访问错误: " + file.getAbsolutePath(), e);

        // 显示详细错误信息
        new AlertDialog.Builder(this)
                .setTitle("文件打开失败")
                .setMessage("无法访问以下路径的文件：\n" + file.getAbsolutePath() +
                        "\n\n可能原因：\n1. 文件路径不受支持\n2. SD卡未正确挂载")
                .setPositiveButton("确定", null)
                .show();
    }

    public boolean isImageFile(File file) {
        if (file == null) return false;

        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1) return false; // 无后缀名

        String extension = fileName.substring(dotIndex + 1).toLowerCase();
        for (String imgExt : IMAGE_EXTENSIONS) {
            if (imgExt.equals(extension)) {
                return true;
            }
        }
        return false;
    }


    // 更新提示对话框
    private void showNoHandlerDialog(File file) {
        new AlertDialog.Builder(this)
                .setTitle("无法打开文件")
                .setMessage("没有找到可以打开 " + file.getName() + " 的应用程序")
                .setPositiveButton("确定", null)
                .show();
    }

    private void updatePathDisplay(String path) {
        String displayText = currentPath == null ?
                "选择存储位置" :
                path.replace(Environment.getExternalStorageDirectory().getPath(), "内部存储");
        tvCurrentPath.setText(displayText);
    }

    @Override
    public void onBackPressed() {
        if (currentPath == null) {
            super.onBackPressed();
        } else {
            File currentDir = new File(currentPath);
            String parent = currentDir.getParent();
            if (parent != null && !parent.equals(getParentStoragePath())) {
                loadDirectory(parent);
            } else {
                showStorageRoots();
            }
        }
    }

    // 权限检查相关方法
    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(
                    this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public void navigateTo(File target) {

        if (target.isDirectory()) {
            loadDirectory(target.getAbsolutePath());
        } else {
            openFile(target);
        }
    }

    private void sortFiles(List<File> files) {
        // 按文件名升序排列（忽略大小写）
        Collections.sort(files, (f1, f2) -> {
            // 处理可能的null值（虽然正常情况下不会出现）
            if (f1 == null || f2 == null) return 0;
            return f1.getName().compareToIgnoreCase(f2.getName());
        });
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                showStorageRoots();
            } else {
                Toast.makeText(this, "需要存储权限才能使用本应用", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    // 存储设备项
    private static class StorageVolumeItem extends File {
        private final String displayName;
        private final boolean isRemovable;

        StorageVolumeItem(File path, String name, boolean removable) {
            super(path.getAbsolutePath());
            this.displayName = name;
            this.isRemovable = removable;
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

        private static final int TYPE_STORAGE = 0;
        private static final int TYPE_FILE = 1;
        private static final int TYPE_BACK = 2;

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
            if (viewType == TYPE_STORAGE) {
                View view = inflater.inflate(R.layout.item_storage, parent, false);
                return new StorageHolder(view);
            }
            View view = inflater.inflate(R.layout.item_file, parent, false);
            return new FileHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object item = items.get(position);

            if (holder instanceof StorageHolder && item instanceof StorageVolumeItem) {
                StorageVolumeItem storage = (StorageVolumeItem) item;
                StorageHolder sh = (StorageHolder) holder;
                sh.icon.setText(storage.isRemovable ? "💾" : "📱");
                sh.name.setText(storage.displayName);
                sh.itemView.setOnClickListener(v -> {
                    ((MainActivity) context).navigateTo(storage);
                });

            } else if (holder instanceof FileHolder) {
                FileHolder fh = (FileHolder) holder;

                if (item instanceof BackItem) {
                    fh.icon.setText("⬆️");
                    fh.name.setText("返回上级");
                    fh.itemView.setOnClickListener(v -> {
                        File currentDir = new File(((MainActivity) context).currentPath);
                        String parent = currentDir.getParent();
                        if (parent != null) {
                            ((MainActivity) context).loadDirectory(parent);
                        }
                    });
                } else if (item instanceof File) {
                    File file = (File) item;
                    fh.icon.setText(file.isDirectory() ? "📁" : "📄");
                    fh.name.setText(file.getName());
                    fh.itemView.setOnClickListener(v -> {
                        ((MainActivity) context).navigateTo(file);
                    });
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            Object item = items.get(position);
            if (item instanceof StorageVolumeItem) return TYPE_STORAGE;
            if (item instanceof BackItem) return TYPE_BACK;
            return TYPE_FILE;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class StorageHolder extends RecyclerView.ViewHolder {
            TextView icon, name;

            StorageHolder(View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.storage_icon);
                name = itemView.findViewById(R.id.storage_name);
            }
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