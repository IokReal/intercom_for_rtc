(function(){
    'use strict';
    console.log("skript started");
    if (window.funny == true) {
        return;
    }
    window.funny = true;
    console.log("fun begine");
    const _setRequestHeader = XMLHttpRequest.prototype.setRequestHeader;
    const _open = XMLHttpRequest.prototype.open;
    const _send = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.send = function(...rest){
        return _send.call(this, ...rest)
    }
    XMLHttpRequest.prototype.open = function(method, url, ...rest){
        this._url = url;
        return _open.call(this, method, url, ...rest);
    };
    XMLHttpRequest.prototype.setRequestHeader = function(name, value){
        try {
            if (this._url && this._url == "https://household.key.rt.ru/api/v2/app/devices/barrier" && name == "Authorization") {
                console.debug("token:", value);
                AndroidBridge.send(value);
            }
        } catch (e) {
            console.warn('Header hook error', e);
        }
    return _setRequestHeader.call(this, name, value);
    };
})();