# HelloBinder

通常我们在做开发时，实现进程间通信用的最多的就是 `AIDL`。当我们定义好 `AIDL` 文件，在编译时编译器会帮我们生成代码实现 `IPC` 通信。借助 `AIDL` 编译以后的代码能帮助我们进一步理解 `Binder IPC` 的通信原理。

但是无论是从可读性还是可理解性上来看，编译器生成的代码对开发者并不友好。比如一个 `BookManager.aidl` 文件对应会生成一个 `BookManager.java` 文件，这个 `java` 文件包含了一个 `BookManager` 接口、一个 `Stub` 静态的抽象类和一个 `Proxy` 静态类。`Proxy` 是 `Stub` 的静态内部类，`Stub` 又是 `BookManager` 的静态内部类，这就造成了可读性和可理解性的问题。

> `Android` 之所以这样设计其实是有道理的，因为当有多个 `AIDL` 文件的时候把 `BookManager`、`Stub`、`Proxy` 放在同一个文件里能有效避免 `Stub` 和 `Proxy` 重名的问题。

实现过程中用到的一些类

- **Binder** : `IBinder` 是一个接口，代表了一种跨进程通信的能力。只要实现了这个接口，这个对象就能跨进程传输。
- **IInterface** : `IInterface` 代表的就是 `Server` 进程对象具备什么样的能力（能提供哪些方法，其实对应的就是 `AIDL` 文件中定义的接口）
- **Binder** : `Java` 层的 `Binder` 类，代表的其实就是 `Binder` 本地对象。`BinderProxy` 类是 `Binder` 类的一个内部类，它代表远程进程的 `Binder ` 对象的本地代理；这两个类都继承自 `IBinder`, 因而都具有跨进程传输的能力；实际上，在跨越进程的时候，`Binder` 驱动会自动完成这两个对象的转换。
- **Stub** : `AIDL` 的时候，编译工具会给我们生成一个名为 `Stub` 的静态内部类；这个类继承了 `Binder`，说明它是一个 `Binder` 本地对象，它实现了 `IInterface` 接口，表明它具有 `Server` 承诺给 `Client` 的能力；`Stub` 是一个抽象类，具体的 `IInterface` 的相关实现需要开发者自己实现。

首先定义 `Client` 与 `Service` 端，分别是 `ClientActivity` 和 `RemoteService` 

```java
// Client 端
public class ClientActivity extends AppCompatActivity {

// Service 端
public class RemoteService extends Service {
```

如何定义统一的行为？那就得考上面说的 `IInterface` 。

它代表的就是服务端进程具体什么样的能力。因此我们需要定义一个 `BookManager` 接口，`BookManager` 继承自 `IIterface`，表明服务端具备什么样的能力。

```java
/**
 * 这个类用来定义服务端 RemoteService 具备什么样的能力
 */
public interface BookManager extends IInterface {
    List<Book> getBooks() throws RemoteException;
    void addBook(Book book) throws RemoteException;
}
```

有了统一的行为，我们就需要实现跨进程调用了。

`Stub` 继承 `Binder`，说明它是一个 `Binder` 本地对象；实现 `IInterface` 接口，表明具有 `Server` 承诺给 `Client` 的能力；`Stub` 是一个抽象类，具体的 `IInterface` 的相关实现需要调用方自己实现。

```java
public abstract class Stub extends Binder implements BookManager {
    //...
    public static BookManager asInterface(IBinder binder) {
        if (binder == null)
            return null;
        IInterface iin = binder.queryLocalInterface(DESCRIPTOR);
        if (iin != null && iin instanceof BookManager)
            return (BookManager) iin;
        return new Proxy(binder);
    }

    @Override
    public IBinder asBinder() {
        return this;
    }

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        switch (code) {
            case INTERFACE_TRANSACTION:
                reply.writeString(DESCRIPTOR);
                return true;
            case TRANSAVTION_getBooks:
                data.enforceInterface(DESCRIPTOR);
                List<Book> result = this.getBooks();
                reply.writeNoException();
                reply.writeTypedList(result);
                return true;
            case TRANSAVTION_addBook:
                data.enforceInterface(DESCRIPTOR);
                Book arg0 = null;
                if (data.readInt() != 0) {
                    arg0 = Book.CREATOR.createFromParcel(data);
                }
                this.addBook(arg0);
                reply.writeNoException();
                return true;
        }
        return super.onTransact(code, data, reply, flags);
    }
    //...
}
```

**`Stub` 类**中重点介绍下 `asInterface` 和 `onTransact`。

`asInterface`，当 `Client` 端在创建和服务端的连接，调用 `bindService` 时需要创建一个 `ServiceConnection` 对象作为入参。在 `ServiceConnection` 的回调方法 `onServiceConnected` 中 会通过这个 `asInterface(IBinder binder)` 拿到 `BookManager` 对象，这个 `IBinder` 类型的入参 `binder` 是驱动传给我们的，正如你在代码中看到的一样，方法中会去调用 `binder.queryLocalInterface()` 去查找 `Binder` 本地对象，如果找到了就说明 `Client` 和 `Server` 在同一进程，那么这个 `binder` 本身就是 `Binder` 本地对象，可以直接使用。否则说明是 `binder` 是个远程对象，也就是 `BinderProxy`。因此需要我们创建一个**代理对象 `Proxy`**，通过这个代理对象来是实现远程访问。

```java
    private void attemptToBindService() {
        Intent intent = new Intent(this, RemoteService.class);
        intent.setAction("com.calmcenter.ipc.server");
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            isConnection = true;
            // 获取 BookManager，如果不在一个进程那就返回 Proxy
            bookManager = Stub.asInterface(service);
            if (bookManager != null) {
                try {
                    List<Book> books = bookManager.getBooks();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isConnection = false;
        }
    };
```

**代理类 Proxy**

```java
public class Proxy implements BookManager {
    //...
    public Proxy(IBinder remote) {
        this.remote = remote;
    }

    @Override
    public void addBook(Book book) throws RemoteException {
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
    //...
}
```

如果是远程调用，`Client` 想要调用 `Server` 的方法就需要通过 `Binder` 代理来完成，也就是上面的 `Proxy`。

在 `Proxy` 中的 `addBook()` 方法中首先通过 `Parcel` 将数据序列化，然后调用 `remote.transact()`。正如前文所述 `Proxy` 是在 `Stub` 的 `asInterface` 中创建，能走到创建 `Proxy` 这一步就说明 `Proxy` 构造函数的入参是 `BinderProxy`，即这里的 `remote` 是个 `BinderProxy` 对象。

最终通过一系列的函数调用，`Client` 进程通过系统调用陷入内核态，`Client` 进程中执行 `addBook()` 的线程挂起等待返回；驱动完成一系列的操作之后唤醒 `Server` 进程，调用 `Server` 进程本地对象的 `onTransact()`。最终又走到了 `Stub` 中的 `onTransact()` 中，`onTransact()` 根据函数编号调用相关函数（在 `Stub` 类中为 `BookManager` 接口中的每个函数中定义了一个编号，只不过上面的源码中我们简化掉了；在跨进程调用的时候，不会传递函数而是传递编号来指明要调用哪个函数）；我们这个例子里面，调用了 `Binder` 本地对象的 `addBook()` 并将结果返回给驱动，驱动唤醒 `Client` 进程里刚刚挂起的线程并将结果返回。

这样一次跨进程调用就完成了。