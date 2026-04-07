<template>
  <div>
    <h2>Sessions</h2>
    <table>
      <thead>
        <tr><th>Session UUID</th><th>User</th><th>Client</th><th>IP</th><th>Last Active</th><th>Action</th></tr>
      </thead>
      <tbody>
        <tr v-for="s in sessions" :key="s.sessionUuid">
          <td>{{ s.sessionUuid }}</td>
          <td>{{ s.userId }}</td>
          <td>{{ s.clientId }}</td>
          <td>{{ s.ip }}</td>
          <td>{{ s.lastActiveAt }}</td>
          <td><button @click="kick(s.sessionUuid)">Kick</button></td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script>
import axios from 'axios';
export default {
  data() { return { sessions: [] } },
  async created() {
    const res = await axios.get('/api/admin/sessions');
    this.sessions = res.data;
  },
  methods: {
    async kick(sid) {
      await axios.post('/api/admin/sessions/' + sid + '/kick', { reason: 'admin action' });
      this.sessions = this.sessions.filter(s => s.sessionUuid !== sid);
    }
  }
}
</script>

