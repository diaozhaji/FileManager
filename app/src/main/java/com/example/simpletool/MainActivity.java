package com.example.simpletool;

import android.Manifest;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
                } else if (isSupportedFile(file)) {
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

    private boolean isSupportedFile(File file) {
        if (file == null) return false;
        if (file.isDirectory()) return true;

        String[] supportedExt = {".txt", ".jpg", ".jpeg", ".png"};
        String fileName = file.getName().toLowerCase();
        for (String ext : supportedExt) {
            if (fileName.endsWith(ext)) return true;
        }
        return false;
    }

    private void openFile(File file) {
        try {
            // 验证文件存在且可读
            if (!file.exists() || !file.canRead()) {
                Toast.makeText(this, "文件不可访问", Toast.LENGTH_SHORT).show();
                return;
            }

            // 获取文件MIME类型
            String mimeType = getMimeType(file);

            // 创建URI
            Uri contentUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    file
            );

            // 创建打开意图
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(contentUri, mimeType)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // 验证是否有可用应用
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, "没有可用的应用打开此文件", Toast.LENGTH_SHORT).show();
            }
        } catch (IllegalArgumentException e) {
            handleFileAccessError(file, e);
        }
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

    private String getMimeType(File file) {
        String ext = file.getName()
                .substring(file.getName().lastIndexOf(".") + 1)
                .toLowerCase();

        switch (ext) {
            case "txt":
                return "text/plain";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            default:
                return "*/*";
        }
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

    private String getSDCardPath() {
        File[] externals = getExternalFilesDirs(null);
        for (File f : externals) {
            String path = f.getAbsolutePath();
            // 检测典型SD卡路径模式
            if (path.matches("/storage/[A-Z0-9]{4}-[A-Z0-9]{4}")) {
                return path.replace("/Android/data/" + getPackageName() + "/files", "");
            }
        }
        return null;
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