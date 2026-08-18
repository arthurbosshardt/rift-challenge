/** Proxies challenge Open Graph HTML from the backend for link preview crawlers. */
module.exports = async (req, res) => {
  const slug = typeof req.query.slug === 'string' ? req.query.slug : '';
  const apiBase = process.env.API_BASE_URL;

  if (!slug) {
    res.status(400).send('Missing challenge slug');
    return;
  }

  if (!apiBase) {
    res.status(500).send('Missing API_BASE_URL');
    return;
  }

  const upstream = await fetch(
    `${apiBase.replace(/\/$/, '')}/api/challenges/share/${encodeURIComponent(slug)}/preview`,
    {
      headers: {
        Accept: 'text/html',
      },
    },
  );

  const body = await upstream.text();
  res.setHeader('Content-Type', 'text/html; charset=utf-8');
  res.status(upstream.status).send(body);
};
