#startlabs#

StartLabs is written entirely in [Clojure](http://www.clojure.org) and ClojureScript (Clojure which compiles down to JavaScript).

For persistence, we use the free edition of [Datomic](http://www.datomic.com).

We use the Web Framework, [Noir](http://webnoir.org/), to make our job easy.

If you're not familiar with Clojure, I strongly suggest working through the 
[tutorials](http://webnoir.org/tutorials) on the Noir website.

[Leiningen](https://github.com/technomancy/leiningen) is the canonical project 
manager for Clojure, so make sure to grab that before doing anything else.

To setup the application, you'll have to fill your Google, AWS, and email account 
credentials into a file called `.lein-env` in the application's root. I've included
a `.lein-env-template` to get you started.

While building the site, you will need to have an instance of Datomic running locally.
To do so, [download a copy of Datomic Free](http://downloads.datomic.com/free.html).
Rename the folder to `datomic-free` and place it at the root of this directory.
Make sure the version you download matches the number indicated in `project.clj`
(0.8.3488 as of writing this):
```
[com.datomic/datomic-free "0.8.3488" ...]
```

Please read the [overview of Datomic](http://www.datomic.com/overview.html) 
to get an understanding for how it works. This is not your mother's SQL database.

You have to start up the Transactor. I've made a tiny shell script called transact.sh to do this.
So in a terminal, just run:
```
./transact.sh
```

If you want to start modifying the guts of the application, the two places to look are `src`
and `resources` (for stylesheets and external js). We use [cljx](https://github.com/lynaghk/cljx), 
an intermediate format, for code that we want to run both client and server-side.
Whenever you modify a .cljx file, make sure to run `lein cljx` to recompile things.

If you're editing the clojurescript files (in `src/cljs`), run 
[cljsbuild](https://github.com/emezeske/lein-cljsbuild) to automatically
compile your clojurescripts into JavaScript: `lein cljsbuild auto`

Stylesheets are preprocessed with less.js. Less was chosen for compatability with Twitter Bootstrap.
We're using a forked version of bootstrap, plus a separate stylesheet called `style.less`, which 
is located in `resources/public/style.less`.

Apologies for all of the moving parts. It may be difficult to get setup initially,
but I think you'll find that working in Clojure is drastically more pleasant than
most other languages/toolkits.

Once everything is setup nicely, just call `lein run`.

I haven't gotten around to writing a nice test suite yet. Shame on me. Hope to get around to it soon.