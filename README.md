#StartLabs#

##Getting Started

StartLabs is written entirely in [Clojure](http://www.clojure.org) and ClojureScript (Clojure which compiles down to JavaScript).

It relies on a modified version of Twitter Bootstrap and (within Bootstrap), Bootstrap Datepicker. These have been included as git submodules, so please run:

```
git submodule update --init --recursive
```

after cloning this repository for the first time.

We use the free edition of [Datomic](http://www.datomic.com) as our database.
The site is written with [Compojure](https://github.com/weavejester/compojure), a simple Clojure routing framework that sits atop [Ring](https://github.com/mmcgrana/ring).

If you're not familiar with Clojure, I strongly suggest doing some puzzles on [4clojure](http://www.4clojure.com/).

[Leiningen](https://github.com/technomancy/leiningen) is the canonical project 
manager for Clojure, so make sure to install that before doing anything else (see the link for instructions).

To setup the application, you'll have to fill your Google, AWS, and email account 
credentials into a file called `.lein-env` in the application's root. I've included
a `.lein-env-template` to get you started.

##The Database

While testing the site, you will need to have an instance of Datomic running locally.

Please read the [overview of Datomic](http://www.datomic.com/overview.html) 
to get an understanding for how it works. This is not your mother's SQL database.

You have to start up the Transactor. I recommend using [datomic-free](https://github.com/cldwalker/datomic-free), a simple command-line util, to start up the transactor like so:
```
datomic-free start
```

The only tricky thing is making sure the version number matches
the one specified on `project.clj` (0.8.3692 as of writing this):
```
[com.datomic/datomic-free "0.8.3692" ...]
```

##Tweaking The Site

If you want to start modifying the guts of the application, the main place to look is `src`.
We use [lein-dalap](http://birdseyesoftware.github.com/lein-dalap.docs/), 
in order to share code between the client and server. To see a list of all `.clj` files that are converted with lein-dalap, check out `dalap_rules.clj` in the project root.

If you're editing the ClojureScript files (in `src/cljs`), run 
[cljsbuild](https://github.com/emezeske/lein-cljsbuild) to automatically
compile them into JavaScript: `lein cljsbuild auto`.

Stylesheets are preprocessed with less.js. Less was chosen for compatability with Twitter Bootstrap. We're using a forked version of Bootstrap, and the primary stylesheet specific to StartLabs is located at `bootstrap/less/startlabs.less`. Again, make sure to do `git submodule update --init --recursive;` if you don't see the `bootstrap` folder in this directory's root.

To rebuild all of boostrap, just call `make bootstrap` or `make watch` within the bootstrap directory.

_NOTE:_ We've reverted to editing raw CSS currently. The site's stylesheet is located in `resources/public/css/custom.pretty.css`.

If you've followed all of the above steps correctly, you should now be able
to start the site by calling: `lein ring server-headless 8000`.

##Nginx

If you want to get really fancy (attempt to simulate the actual production 
environment), you can try proxying the site through [nginx](http://nginx.org/). 
This is generally safer and more flexible than running the application process as 
root, so it's the preferred way of configuring things. I've included a configuration 
file here: `conf/startlabs.conf`. It assumes you'll be running the *real* 
application server on port 8000. Tweak it as necessary, then run:
```
sudo nginx -c /The/full/path/to/conf/startlabs.conf
```

##Production Mode Differences##
On the production server, auto-reloading of modified .clj files is disabled.
So, after pulling changes, you'll need to start up a new instance of the 
server on a different port, and then kill the old server.

Use `screen -ls` to determine the name of the currently running screen instance. Then do `screen -R the-screen-instance-name` to load up the current screen instance.

Use `ctrl+a n` to cycle through screens. Find a screen with no active process, and do `lein ring server-headless 8000`, or do port 8001 if 8000 is already in use. Once you've verified that the new server is running, feel free to cycle to the old server and kill it with  `ctrl+c`.

Although this setup avoids any downtime, it could certainly be automated. Still trying to devise a better system. May eventually transition to something along the lines of Elastic Beanstalk.

##Epilogue

I haven't gotten around to writing a test suite yet. 
Shame on me. Hope to do so eventually.

Apologies for all of the moving parts. It may be difficult to get setup initially,
but I think you'll find that working in Clojure is drastically more pleasant than
most other languages/toolkits.

If you run into any issues, just [email me](mailto:ethanis@mit.edu).
