'use strict';

const HELPER_BASE = process.env.HELPER_BASE || "/opt/";
const Response = require(HELPER_BASE + 'response');
const Redirect = require(HELPER_BASE + 'redirect');

const cheerio = require('cheerio');

const jsonfile = require(HELPER_BASE + 'jsonfile-utils');

const yamareco_base_url = "https://api.yamareco.com/api/v1";
const yamareco_slideshow_url = "https://www.yamareco.com/modules/yamareco/include/slideshow.php?did=";
const instagram_image_list_url = 'https://graph.instagram.com/me/media';
const instagram_refresh_url = 'https://graph.instagram.com/refresh_access_token';

const fetch = require('node-fetch');

const INITIAL_ACCESS_TOKEN = 'yInstagram‚Ì‰Šúƒg[ƒNƒ“z';

const TOKEN_FILE_PATH = process.env.THIS_BASE_PATH + '/data/wallpaper/access_token.json';
const USERIDLIST_FILE_PATH = process.env.THIS_BASE_PATH + '/data/wallpaper/userid_list.json';

exports.handler = async (event, context, callback) => {
	if( event.path == '/wallpaper-get' ){
		console.log(event.queryStringParameters);

		var userid_list = await jsonfile.read_json(USERIDLIST_FILE_PATH, [] );
		console.log(userid_list);

		var list = [];
		for( let item of userid_list ){
			var list_yamareco = await get_all_yamareco_list(item);
//			console.log(list_yamareco);
			list = list.concat(list_yamareco);
		}

		var json = await read_token();
		var list_instagram = await get_all_image_list(json.access_token);
//		console.log(list_instagram);

		var list = list.concat(list_instagram);

		var index = make_random(list.length - 1);
		
		return new Redirect(list[index].media_url);
	}else{
		throw "unknown endpoint";
	}
};

async function get_all_yamareco_list(userId){
	var counter = 1;
	var response = await do_get(yamareco_base_url + "/getReclist/user/" + userId + '/' + counter++);
//	console.log(response);
	if( response.err != 0 )
		throw response.errcode;
	var reclist = response.reclist;
	var list = [];
	for( let item of reclist ){
		var response_list = await get_all_yamareco_image_list(item.rec_id);
		list = list.concat(response_list);
	}

	return list;
}

async function get_all_yamareco_image_list(recId){
	var url = yamareco_slideshow_url + recId;
	var response = await do_get_text(url);

	var $ = cheerio.load(response);
	var list = [];
	$(".hidden-container > a").each(function() {
		var link = $(this);
		var href = link.attr("href");
		list.push({ media_url: href });
	});

	return list;
}

async function get_all_image_list(access_token){
    console.log("get_all_image_list called");
    var list = [];
    var url = instagram_image_list_url + '?fields=id,caption,permalink,media_url&access_token=' + access_token;
    do{
        var json = await do_get(url);
        list = list.concat(json.data);
        if( !json.paging.next )
            break;
        url = json.paging.next;
    }while(true);

    return list;
}

function do_get_text(url, qs) {
  var params = new URLSearchParams(qs);

  var params_str = params.toString();
  var postfix = (params_str == "") ? "" : ((url.indexOf('?') >= 0) ? ('&' + params_str) : ('?' + params_str));
  return fetch(url + postfix, {
      method: 'GET',
    })
    .then((response) => {
      if (!response.ok)
        throw new Error('status is not 200');
      return response.text();
    });
}

function do_get(url, qs) {
  var params = new URLSearchParams(qs);

  var params_str = params.toString();
  var postfix = (params_str == "") ? "" : ((url.indexOf('?') >= 0) ? ('&' + params_str) : ('?' + params_str));
  return fetch(url + postfix, {
      method: 'GET',
    })
    .then((response) => {
      if (!response.ok)
        throw new Error('status is not 200');
      return response.json();
    });
}

async function read_token(){
	var json = await jsonfile.read_json(TOKEN_FILE_PATH, { access_token: INITIAL_ACCESS_TOKEN } );
	if( !json.expires_in ){
		json = await refresh_token();
	}
	return json;
}

async function refresh_token(){
	var json = await jsonfile.read_json(TOKEN_FILE_PATH, { access_token: INITIAL_ACCESS_TOKEN } );

	var url = instagram_refresh_url + '?grant_type=ig_refresh_token&access_token=' + json.access_token;
	var result = await do_get(url);
	json.access_token = result.access_token;
	json.expires_in = result.expires_in;

	await jsonfile.write_json(TOKEN_FILE_PATH, json);
	return json;
}

exports.trigger = async (event, context, callback) => {
	console.log('wallpaper cron triggered');

	await refresh_token();
};

function make_random(max) {
	return Math.floor(Math.random() * (max + 1));
}
