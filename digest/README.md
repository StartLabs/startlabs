A *very* simple node.js script.
All it does is make an http GET request for /send-digest.
It grabs the basic auth credentials from `../.lein-env`.
This could easily be replaced with a curl/wget job, but
we're using node so that we can auto-parse the auth credentials
instead of hard-coding them or redundantly storing them elsewhere.

Setup a crontab to run send-digest:

Modify `/etc/crontab`:

```
# m h dom mon dow
# every friday at noon
0 12 * * 5 root cd /root/startlabs/digest && node send-digest.js¬
```
