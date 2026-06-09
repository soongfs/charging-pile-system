# Charging Pile Frontend

Vue 3 + Vite 充电桩前端项目，接口地址固定为 `http://localhost:8080`。

## 安装与运行

```bash
npm install
npm install vue-router axios
npm run dev
```

## 页面

- `/`：登录入口，提供用户端和管理员端入口。
- `/user`：用户端，左侧步骤条切换注册、请求、队列、进度和账单。
- `/admin`：管理员端，左侧深色侧边栏切换充电桩、队列和计费参数。

## 接口

所有接口都内联在 `src/api/index.js`，响应格式为 `{ code, message, data }`，其中 `code === 0` 视为成功。

车辆标识字段统一使用英文 `carId`。
