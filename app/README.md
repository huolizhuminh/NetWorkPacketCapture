# Doc

## 作用
* 使手机在wifi的设备的同时能够通过4g上网。解决的场景需求有两个：对于有无智能网络切换的手机默认使用4g上网，网络请求默认使用wifi通道。
此时不能上外网。对于有智能网络切换的手机如pixel 三星s8等手机，连上wifi设备之后，如果wifi设备上不了网，则所有网络请求默认走4g通道。
此时不能访问wifi设备。此时本模块能够拦截手机所有网络请求（包括非本app的网络请求），并且通过访问的IP地址重新指定通道。本模块提供的VPNSocketFactory
能够
## 步骤
* 首先要检测write setting权限是否被授予，未授予则打开。一定要检测，否则6.0以及6.1的部分机型会崩溃。
* 打开vpn 具体步骤见demo。
* 为减少vpn通道的堵塞，对于指向wifi设备的请求，如果使用的OkHttp,则在新建OkHttpClient的同时传入VPNSocketFactory
如果直接通过socket连接，则使用SocketChannel,通过SocketChannel得到socket让其不走vpn通道。






