var edn  = require("jsedn");
var fs   = require("fs");
var http = require("http");

var lein_env_fname = "../.lein-env";

var lein_env = fs.readFileSync(lein_env_fname, 'utf8');
var env_edn = edn.parse(lein_env);

var username = "pi";
var password = env_edn.at("pi-password");

var is_dev = env_edn.at("dev");

var host = is_dev ? "localhost" : "www.startlabs.org";
var port = is_dev ? 8000 : 80;

var request_options = {
  host: host,
  port: port,
  path: "/send-digest",
  headers: {
   "Authorization": "Basic " 
     + new Buffer(username+":"+password).toString('base64')
  }
};

var request = http.get(request_options, function(response){
  console.log('Status: '  + response.statusCode);
  console.log('Headers: ' + JSON.stringify(response.headers));
});
