package net.mtgsaber.uni_projects.cs4504groupproject.util;

public class Container<T> {
    private volatile T val;

    public Container(T val) {
        this.val = val;
    }

    public T get() {
        return val;
    }

    public void set(T val) {
        this.val = val;
    }
}
