# run this after installing git and cloning startlabs
# sudo apt-get install git;
# git clone https://github.com/sherbondy/startlabs

sudo apt-get update
sudo apt-get install nginx openjdk-7-jre-headless make python-software-properties zsh nodejs npm

# make the nginx log directory
sudo mkdir /etc/nginx/logs

#cleanup all of the package gunk
sudo apt-get autoremove

#setup clojure
curl https://raw.github.com/technomancy/leiningen/preview/bin/lein > lein
chmod 755 lein
sudo mv lein /usr/local/bin

#grab startlabs submodules
git submodule init && git submodule update
sudo npm install recess connect uglify-js jshint -g
cd bootstrap; make bootstrap

# now all that's left to do is configure datomic and .lein-env.
# I should automate this eventually.
