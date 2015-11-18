package org.jenkinsci.deprecatedusage;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

public class JenkinsFile {
    // relative to user dir
    private static final File WORK_DIRECTORY = new File("work");

    private static final ThreadFactory DAEMON_THREAD_FACTORY = new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            final Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        }
    };
    private static final ExecutorService downloadExecutorService = Executors.newFixedThreadPool(8,
            DAEMON_THREAD_FACTORY);

    private final String name;
    private final String version;
    private final URL url;
    private final File file;
    private Future<?> downloadFuture;

    public JenkinsFile(String name, String version, String url) throws MalformedURLException {
        super();
        this.name = name;
        this.version = version;
        this.url = new URL(url);
        final String fileName = url.substring(url.lastIndexOf('/'));
        this.file = new File(WORK_DIRECTORY, name + '/' + version + '/' + fileName);
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public File getFile() {
        return file;
    }

    public void startDownloadIfNotExists() {
        if (file.exists()) {
            // if file is already downloaded, do not download again
            return;
        }
        downloadFuture = downloadExecutorService.submit(new Callable<Object>() {
            @Override
            public Object call() throws IOException {
                final File tempFile = File.createTempFile("deprecated-usage-in-plugins-", ".tmp");
                try {
                    final OutputStream output = new BufferedOutputStream(new FileOutputStream(
                            tempFile));
                    try {
                        new HttpGet(url).copy(output);
                    } finally {
                        output.close();
                    }
                    // write target file only if complete
                    file.getParentFile().mkdirs();
                    Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE);
                    Log.log("Downloaded " + file.getName() + ", " + file.length() / 1024 + " Kb");
                    // TODO delete old versions
                } finally {
                    tempFile.delete();
                }
                return null;
            }
        });
    }

    public void waitDownload() throws Exception {
        if (downloadFuture != null) {
            try {
                downloadFuture.get();
            } catch (final ExecutionException e) {
                if (e.getCause() instanceof Exception) {
                    throw (Exception) e.getCause();
                } else {
                    throw e;
                }
            }
        }
    }
}