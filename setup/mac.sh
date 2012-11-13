echo Installing all of the junk. Hope this works.
echo Grabbing git submodules
cd ..
git submodule init && git submodule update

if ! type "lein" > /dev/null 2>&1; then
  echo Installing lein to /usr/local/bin
  curl https://raw.github.com/technomancy/leiningen/preview/bin/lein > lein
  chmod +x lein
  mv lein /usr/local/bin
else
  echo Lein already installed, skipping.
fi

if ! type "brew" > /dev/null 2>&1; then
  echo Installing brew
  ruby -e "$(curl -fsSkL raw.github.com/mxcl/homebrew/go)"
else
  echo Homebrew already installed, skipping.
fi

echo Compiling cljx files. Give it a second.
lein cljx

if ! type "node" > /dev/null 2>&1; then
  echo Installing node and npm
  brew install node
  curl https://npmjs.org/install.sh | sh
else
  echo Node already installed, skipping.
fi

if ! type "watchr" > /dev/null 2>&1; then
  echo Installing watchr
  sudo gem install watcher
fi

if ! type "recess" > /dev/null 2>&1; then
  echo Installing npm packages for bootstrap
  sudo npm install recess connect uglify-js jshint -g
else
  echo Recess already installed. Hopefully you have the bootstrap deps.
  echo If not, just do: "sudo npm install recess connect uglify-js jshint -g"
fi

echo Making bootstrap
cd bootstrap; rm -rf bootstrap; make bootstrap;

echo ---
echo If you just got a weird error about recess, you need to update your path.
echo Try adding /usr/local/share/npm/bin to your path

# still need to do nice .lein-env bit
