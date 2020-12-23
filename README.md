# HelloBinder

通常我们在做开发时，实现进程间通信用的最多的就是 `AIDL`。当我们定义好 `AIDL` 文件，在编译时编译器会帮我们生成代码实现 `IPC` 通信。借助 `AIDL` 编译以后的代码能帮助我们进一步理解 `Binder IPC` 的通信原理。

但是无论是从可读性还是可理解性上来看，编译器生成的代码对开发者并不友好。比如一个 `BookManager.aidl` 文件对应会生成一个 `BookManager.java` 文件，这个 `java` 文件包含了一个 `BookManager` 接口、一个 `Stub` 静态的抽象类和一个 `Proxy` 静态类。`Proxy` 是 `Stub` 的静态内部类，`Stub` 又是 `BookManager` 的静态内部类，这就造成了可读性和可理解性的问题。

> `Android` 之所以这样设计其实是有道理的，因为当有多个 `AIDL` 文件的时候把 `BookManager`、`Stub`、`Proxy` 放在同一个文件里能有效避免 `Stub` 和 `Proxy` 重名的问题。

实现过程中用到的一些类

- **Binder** : `IBinder` 是一个接口，代表了一种跨进程通信的能力。只要实现了这个接口，这个对象就能跨进程传输。
- **IInterface** : `IInterface` 代表的就是 `Server` 进程对象具备什么样的能力（能提供哪些方法，其实对应的就是 `AIDL` 文件中定义的接口）
- **Binder** : `Java` 层的 `Binder` 类，代表的其实就是 `Binder` 本地对象。`BinderProxy` 类是 `Binder` 类的一个内部类，它代表远程进程的 `Binder ` 对象的本地代理；这两个类都继承自 `IBinder`, 因而都具有跨进程传输的能力；实际上，在跨越进程的时候，`Binder` 驱动会自动完成这两个对象的转换。
- **Stub** : `AIDL` 的时候，编译工具会给我们生成一个名为 `Stub` 的静态内部类；这个类继承了 `Binder`，说明它是一个 `Binder` 本地对象，它实现了 `IInterface` 接口，表明它具有 `Server` 承诺给 `Client` 的能力；`Stub` 是一个抽象类，具体的 `IInterface` 的相关实现需要开发者自己实现。