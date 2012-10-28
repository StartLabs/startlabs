# run this after installing git and cloning startlabs
# sudo apt-get install git;
# git clone https://github.com/sherbondy/startlabs

sudo add-apt-repository ppa:chris-lea/node.js
sudo apt-get update
sudo apt-get install nginx openjdk-7-jre-headless make python-software-properties nodejs npm zsh daemontools wget

# make the nginx log directory
sudo mkdir /etc/nginx/logs

#cleanup all of the package gunk
sudo apt-get autoremove

#setup clojure
wget https://raw.github.com/technomancy/leiningen/preview/bin/lein
chmod u+x lein
sudo mv lein /usr/local/bin
#run cljx once
lein cljx

#grab startlabs submodules
cd ..
git submodule init && git submodule update
sudo npm install recess connect uglify-js jshint -g
cd bootstrap; make bootstrap

# now all that's left to do is configure datomic and .lein-env.
# I should automate this eventually.
