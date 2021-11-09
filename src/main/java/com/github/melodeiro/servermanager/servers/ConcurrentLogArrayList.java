package com.github.melodeiro.servermanager.servers;

import com.sun.istack.internal.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * Created by Daniel on 13.03.2017.
 * @author Melodeiro
 */
public class ConcurrentLogArrayList<E> extends ArrayList<E> {

    @Override
    public boolean add(E e) {
        synchronized (this) {
            return super.add(e);
        }
    }

    @Override
    public E remove(int index) {
        synchronized (this) {
            return super.remove(index);
        }
    }

    @Override
    public boolean addAll(int index, @NotNull Collection<? extends E> c) {
        synchronized (this) {
            return super.addAll(index, c);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        Iterator it = this.iterator();

        synchronized (this) {
            while (it.hasNext()) {
                String s = it.next().toString();
                sb.append(s);
                if (it.hasNext())
                    sb.append(System.getProperty("line.separator"));
            }
        }

        return sb.toString();
    }

    @Override
    public int size() {
        synchronized (this) {
            return super.size();
        }
    }
}
