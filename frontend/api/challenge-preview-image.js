/** Proxies challenge Open Graph preview PNG from the backend for link preview crawlers. */
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
    `${apiBase.replace(/\/$/, '')}/api/challenges/share/${encodeURIComponent(slug)}/preview-image.png`,
    {
      headers: {
        Accept: 'image/png',
      },
    },
  );

  if (!upstream.ok) {
    const body = await upstream.text();
    res.status(upstream.status).send(body);
    return;
  }

  const image = Buffer.from(await upstream.arrayBuffer());
  res.setHeader('Content-Type', 'image/png');
  res.setHeader('Cache-Control', 'public, max-age=300');
  res.status(200).send(image);
};
