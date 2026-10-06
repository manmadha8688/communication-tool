# Deploying Neutara CommuniQ

## Before going live

| # | What | Where |
|---|---|---|
| 1 | A server with Docker, and a domain, e.g. `neutaracommuniq.cftools.live` | |
| 2 | **HTTPS** on that domain (reverse proxy / load balancer with a certificate). Part 3 (the live meeting) uses the microphone, which browsers only allow on `https://`. | Hosting |
| 3 | In Azure, the app registration's **Authentication → Single-page application**: add `https://<domain>` (no trailing slash). Not under "Web". | Azure portal |
| 4 | Copy `.env.example` to `.env` on the server and fill it in. Never commit it. | Server |

```
DB_URL=jdbc:postgresql://host.docker.internal:5432/assessment
DB_USER=assessment
DB_PASSWORD=<password>
AZURE_TENANT_ID=<Directory (tenant) ID>
AZURE_CLIENT_ID=<Application (client) ID>
APP_JWT_SECRET=<random, 32+ characters>
APP_ADMIN_EMAILS=aditya.rompella@cloudfuze.com,sujana.manapuram@cloudfuze.com
APP_CORS_ORIGINS=https://<domain>
OPENAI_API_KEY=<key>
```

## Start / update

```
docker compose up -d --build
```

The website runs on `127.0.0.1:5185` and the API on `127.0.0.1:8195`, both reachable only from the
server. The API is also reached through the website at `/api`, and the database is the PostgreSQL
server on the machine (`DB_URL`), not a container. Sign-in is Microsoft only.

## nginx on the server (HTTPS)

`/etc/nginx/sites-available/neutaracommuniq`:

```nginx
server {
    listen 80;
    server_name neutaracommuniq.cftools.live;

    client_max_body_size 12m;

    location / {
        proxy_pass http://127.0.0.1:5185;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 180s;
    }
}
```

```
sudo ln -s /etc/nginx/sites-available/neutaracommuniq /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
# Gets a free certificate and adds HTTPS (port 443 + redirect from http) to the file above.
sudo certbot --nginx -d neutaracommuniq.cftools.live
```

## Checked in a production-mode run

- The site and the API answer on one address; the backend port is closed.
- Admin pages need a Microsoft sign-in (401 without one).
- It will not start if a required setting is missing.
- All 150 questions load on first start.

## Settings you may change

| Setting | Default | Meaning |
|---|---|---|
| `APP_MARKS_TEAMS` / `APP_MARKS_EMAIL` / `APP_MARKS_MEETING` | 20 / 35 / 45 | Marks per part (total 100) |
| `OPENAI_SCORING_MODEL` | gpt-4o | Model that marks answers |
| `OPENAI_MEETING_MODEL` | gpt-realtime | Live meeting voice; `gpt-realtime-mini` is cheaper |

## Back up

The database is your own PostgreSQL server. Back it up with
`pg_dump -U assessment assessment > backup.sql` on the server.

## Admins

Admins are kept in the database table `admin_email`. `APP_ADMIN_EMAILS` only fills it on the very
first start, when the table is empty. After that, change admins in the table; it takes effect on the
person's next click, with no restart:

```
psql -U assessment -d assessment -c "INSERT INTO admin_email (email, added_at) VALUES ('name@cloudfuze.com', now());"
psql -U assessment -d assessment -c "DELETE FROM admin_email WHERE lower(email) = 'name@cloudfuze.com';"
```
