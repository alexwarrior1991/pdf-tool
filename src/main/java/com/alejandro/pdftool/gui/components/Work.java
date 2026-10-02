package com.alejandro.pdftool.gui.components;

import com.alejandro.pdftool.ProgressListener;

/** Trabajo que se ejecuta en segundo plano e informa de su avance. */
@FunctionalInterface
public interface Work<T> {
    T run(ProgressListener progress) throws Exception;
}
