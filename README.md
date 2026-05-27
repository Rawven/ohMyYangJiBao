# 明天割肉吧兄弟 🐔🍗

因为不想给养🐔宝充VIP自己写了个能大概用的

## 启动

### 后端

```bash
cd fund-tracker-server
# 设置 DeepSeek API Key（在 .env.example 查看格式）
export DEEPSEEK_API_KEY=sk-your-key-here
mvn spring-boot:run
```

后端默认启动在 `http://localhost:8080`。

### 前端

```bash
cd fund-tracker-web
npm install
npm run dev
```

前端默认启动在 `http://localhost:3000`，自动代理 API 到后端。
