import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import UserHome from '../views/UserHome.vue'
import AdminHome from '../views/AdminHome.vue'
import Register from '../views/Register.vue'

const routes = [
  { path: '/', name: 'login', component: Login },
  { path: '/register', name: 'register', component: Register },
  { path: '/user', name: 'user-home', component: UserHome },
  { path: '/admin', name: 'admin-home', component: AdminHome },
]

export default createRouter({
  history: createWebHistory(),
  routes,
})
