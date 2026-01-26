# HarmarHttp 🚀

一个 **从零实现的 Java HTTP 服务器**，支持 **HTTP/1.1、HTTPS 以及 HTTP/2**，重点关注协议细节、底层实现与工程结构，适合作为 **HTTP/HTTP2 学习项目、网络编程参考实现**。

> ⚠️ 本项目以学习与研究为目的，不建议直接用于生产环境。

---

## ✨ 项目特性

### 🌐 协议支持

* **HTTP/1.1**

   * GET / POST / HEAD
   * Keep-Alive
   * Chunked Transfer-Encoding
* **HTTPS (TLS 1.2 / 1.3)**

   * 基于 Netty 的 TLS 支持
   * ALPN 协议协商
* **HTTP/2**

   * 帧级实现（HEADERS / DATA / SETTINGS / GOAWAY / RST_STREAM 等）
   * **HPACK 头部压缩（含 Huffman 编码）**
   * 多路复用（Stream）
   * 流管理与调度（Scheduler）

---

## 🏗️ 架构概览

```
                ┌─────────────────────────┐
                │     HarmarHttpServer     │
                │     (核心协调入口)      │
                └───────────┬─────────────┘
                            │
        ┌───────────────────┼────────────────────┐
        │                   │                    │
┌───────▼────────┐  ┌───────▼────────┐  ┌────────▼───────┐
│ ConnectionMgr  │  │ NettyTlsServer │  │     Router     │
│ (HTTP/1.x)     │  │ (HTTPS / h2)   │  │ 路由与分发     │
└────────────────┘  └────────┬────────┘  └────────────────┘
                              │
                    ┌─────────▼─────────┐
                    │   Http2Manager    │
                    │ HTTP/2 核心实现   │
                    └───────────────────┘
```

---

## 📦 模块说明

### 核心模块

* **HarmarHttpServer**

   * 服务器核心入口
   * 组件初始化与协调

* **ConnectionManager**

   * HTTP/1.x 连接管理
   * 基于 `AsynchronousSocketChannel`

* **NettyTlsServer**

   * HTTPS / HTTP2 over TLS
   * ALPN 协议选择（h2 / http1.1）

* **Router**

   * 路由注册与分发
   * 支持参数化路径 `/api/user/{id}`

---

### HTTP/2 实现（`http2/`）

* `Http2Manager`：HTTP/2 协议核心
* `Frame / FrameDecoder`：帧解析
* `HpackEncoder / HpackDecoder`：HPACK 实现
* `Http2Stream`：流状态管理
* `Scheduler`：响应调度与优先级

➡️ **完全手写 HTTP/2 + HPACK，未依赖 Netty 的 HTTP/2 实现**

---

### 🔐 安全模块

* **DosDefender**

   * 基于滑动窗口的 IP 限流
   * 防止 DoS / 资源耗尽攻击

* **路径规范化**

   * 防止目录遍历（`../`）攻击

* **大文件并发限制**

   * 单 IP 同时访问大文件数限制

---

### 📊 性能监控

* **PerformanceMonitor**

   * 请求总数 / 成功 / 失败
   * 平均 / 最大 / 最小响应时间
   * 并发连接数
   * HTTP 状态码分布

* **内置监控 API**

| Endpoint   | 描述         |
| ---------- | ---------- |
| `/health`  | 健康检查       |
| `/metrics` | 性能指标       |
| `/stats`   | 统计数据       |
| `/reset`   | 重置统计（POST） |

---

### 📁 静态文件服务

* LRU 文件缓存
* 文件修改时间自动失效
* 减少磁盘 IO
* 支持图片 / HTML / JS / ZIP 等

---

## 📂 项目结构

```
src/main/java/org/example
├── HarmarHttpServer.java
├── Main.java
├── Router.java
├── ConnectionManager.java
├── http2/            # HTTP/2 协议实现
├── https/            # HTTPS / TLS
├── monitor/          # 性能监控
├── security/         # 安全模块
├── protocol/         # HTTP 协议解析
└── connection/       # 底层连接抽象
```

---

## 🚀 快速开始

### 构建

```bash
mvn clean compile
```

### 运行

```bash
mvn exec:java -Dexec.mainClass="org.example.Main"
```

---

## 🌍 访问示例

| 协议            | 地址                                                         |
| ------------- | ---------------------------------------------------------- |
| HTTP/1.1      | [http://localhost:80](http://localhost:80)                 |
| HTTPS / HTTP2 | [https://localhost:8443](https://localhost:8443)           |
| Metrics       | [http://localhost:80/metrics](http://localhost:80/metrics) |

示例资源：

* `/index.html`
* `/nijika.jpg`
* `/big_file.zip`

---

## 🎯 适用场景

✅ 学习 HTTP / HTTP2 协议细节
✅ 网络编程与 NIO 实践
✅ 简历 / 技术展示项目

❌ 不适合高并发生产环境
❌ 不提供完整 Web 框架能力

---

## 🧠 项目亮点

* **完整 HTTP/2 + HPACK 手写实现**
* 清晰的模块化设计
* 安全与性能意识（限流 / 缓存 / 监控）
* 非黑盒，适合深入阅读源码

---

## 📜 License

MIT License

---

如果你正在学习 **HTTP / HTTP2 / 网络编程 / Java NIO**，这个项目会非常适合你 👋
