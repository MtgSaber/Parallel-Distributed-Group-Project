package net.mtgsaber.uni_projects.cs4504groupproject.data;

import net.mtgsaber.uni_projects.cs4504groupproject.config.Config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.concurrent.TimeoutException;

public final class Resource {
    private final File file;
    private final FileType type;
    private volatile boolean inUse;
    private volatile boolean registered;

    public Resource(File file) throws FileNotFoundException {
        if (!file.exists()) throw new FileNotFoundException("No such file: " + file.toString());
        this.file = file;
        this.type = null; // TODO: get file type from file name/class-instance
        this.registered = true;
    }

    /**
     * returns a fresh input stream of this resource's file as soon as the resource is not in use.
     * @return a new FileInputStream on this resource's file; null if a FileNotFoundException is caught.
     * @throws InterruptedException if the wait loop is interrupted for any reason.
     * @throws TimeoutException if the
     */
    public synchronized FileInputStream getFileStream() throws InterruptedException, TimeoutException {
        /*
        long startTime = System.currentTimeMillis();

        if (!registered) return null;

        while (inUse) {
            Thread.currentThread().sleep(200);
            if (System.currentTimeMillis() - startTime >= config.RES_MAX_USAGE_TIME)
                throw new TimeoutException("Waited more than " + config.RES_MAX_USAGE_TIME + "ms for this resource to open.");
            if (!registered) return null;
        }

        if (!registered) return null;

        inUse = true;
        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException fnfex) {
            return null;
        }
        */
        return null;
    }

    /**
     * releases the file for other threads to access it.
     */
    public synchronized void release() {
        inUse = false;
    }

    /**
     * simple wrapper method.
     * @return file.exists()
     */
    public boolean exists() {
        return file.exists();
    }

    public File getFile() { return file; }

    public void unRegister() { registered = false; }
}
