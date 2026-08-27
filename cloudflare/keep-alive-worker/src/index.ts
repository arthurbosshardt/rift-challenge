interface Env {
  HEALTH_URL: string;
}

export default {
  async scheduled(_event: ScheduledEvent, env: Env, ctx: ExecutionContext) {
    ctx.waitUntil(
      fetch(env.HEALTH_URL)
        .then((res) => console.log(`Keep-alive ping: ${res.status}`))
        .catch((err) => console.error(`Keep-alive ping failed: ${err}`)),
    );
  },
};
