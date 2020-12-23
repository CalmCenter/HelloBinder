package com.calmcenter.ipc.proxy;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import com.calmcenter.ipc.Book;
import com.calmcenter.ipc.server.BookManager;
import com.calmcenter.ipc.server.Stub;

import java.util.List;


public class Proxy implements BookManager {

    private static final String DESCRIPTOR = "com.calmcenter.ipc.server.BookManager";

    private IBinder remote;

    public Proxy(IBinder remote) {

        this.remote = remote;
    }

    public String getInterfaceDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public List<Book> getBooks() throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel replay = Parcel.obtain();
        List<Book> result;

        try {
            data.writeInterfaceToken(DESCRIPTOR);
            remote.transact(Stub.TRANSAVTION_getBooks, data, replay, 0);
            replay.readException();
            result = replay.createTypedArrayList(Book.CREATOR);
        } finally {
            replay.recycle();
            data.recycle();
        }
        return result;
    }

    @Override
    public void addBook(Book book) throws RemoteException {
        // book 代理对象
        Parcel data = Parcel.obtain();
        Parcel replay = Parcel.obtain();

        try {
            data.writeInterfaceToken(DESCRIPTOR);
            if (book != null) {
                data.writeInt(1);
                book.writeToParcel(data, 0);
            } else {
                data.writeInt(0);
            }
            remote.transact(Stub.TRANSAVTION_addBook, data, replay, 0);
            replay.readException();
        } finally {
            replay.recycle();
            data.recycle();
        }
    }

    @Override
    public IBinder asBinder() {
        return remote;
    }
}
