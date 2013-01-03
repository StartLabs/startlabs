#StartLabs#

##Getting Started

StartLabs is written entirely in [Clojure](http://www.clojure.org) and ClojureScript (Clojure which compiles down to JavaScript).

It relies on Twitter Bootstrap and Leaflet (for mapping), which have been included as
git submodules, so please run:

`git submodule init && git submodule update;`

after cloning this repository for the first time.

We use the free edition of [Datomic](http://www.datomic.com) as our database.
The site is written with [Compojure](https://github.com/weavejester/compojure), a simple, Clojure routing framework that sits atop [Ring](https://github.com/mmcgrana/ring)

If you're not familiar with Clojure, I strongly suggest doing some puzzles on [4clojure](http://www.4clojure.com/).

[Leiningen](https://github.com/technomancy/leiningen) is the canonical project 
manager for Clojure, so make sure to install that before doing anything else (See the link for instructions).

To setup the application, you'll have to fill your Google, AWS, and email account 
credentials into a file called `.lein-env` in the application's root. I've included
a `.lein-env-template` to get you started.

##The Database

While testing the site, you will need to have an instance of Datomic running locally.
It should have appeared in the root directory as `datomic-free` when you downloaded
the git submodules. The only tricky thing is making sure the version number matches
the one specified on `project.clj` (0.8.3692 as of writing this):
```
[com.datomic/datomic-free "0.8.3692" ...]
```

Please read the [overview of Datomic](http://www.datomic.com/overview.html) 
to get an understanding for how it works. This is not your mother's SQL database.

You have to start up the Transactor. I recommend using [datomic-free](https://github.com/cldwalker/datomic-free), a simple command-line util, to start up the transactor like so:
```
datomic-free start
```

##Tweaking The Site

If you want to start modifying the guts of the application, the main place to look is `src`.
We use [cljx](https://github.com/lynaghk/cljx), 
an intermediate format, for code that we want to run both client and server-side.
Whenever you modify a .cljx file, make sure to run `lein cljx` to recompile things.

If you're editing the ClojureScript files (in `src/cljs`), run 
[cljsbuild](https://github.com/emezeske/lein-cljsbuild) to automatically
compile them into JavaScript: `lein cljsbuild auto`

Stylesheets are preprocessed with less.js. Less was chosen for compatability with Twitter Bootstrap.
We're using a forked version of Bootstrap, and the primary stylesheet specific to StartLabs
is located at `bootstrap/less/startlabs.less`. Again, make sure to do `git submodule init && git submodule update;` if you don't see the `bootstrap` folder in this directory's root.
To rebuild all of boostrap, just call `make bootstrap` or `make watch` within the bootstrap directory.

*NOTE:* We've reverted to editing raw CSS currently. The site's stylesheet is located in `resources/public/css/custom.pretty.css`.

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
Currently, whenever you edit a .clj file, you need to recompile by restarting the server.
Use `screen -R` then press tab to load up the current screen instance.
Use `ctrl+a n` to cycle through screens.
Find the screen corresponding the server. Kill it with `ctrl+c`.
Then type `supervise startlabs` to recompile the server and get it running once more.
This is a poor, temporary solution. Still trying to devise a better setup.

##Epilogue

I haven't gotten around to writing a test suite yet. 
Shame on me. Hope to do so eventually.

Apologies for all of the moving parts. It may be difficult to get setup initially,
but I think you'll find that working in Clojure is drastically more pleasant than
most other languages/toolkits.

If you run into any issues, just [email me](mailto:ethanis@mit.edu).