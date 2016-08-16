scout.RemoteApp = function() {

};
scout.inherits(scout.RemoteApp, scout.App);

scout.RemoteApp.prototype._doBootstrap = function(options) {
  return [
    scout.logging.bootstrap(),
    scout.device.bootstrap(),
    scout.defaultValues.bootstrap(),
    scout.fonts.bootstrap(options.fonts)
  ];
};

/**
 * @override
 */
scout.RemoteApp.prototype._createSession = function($entryPoint, options) {
  options = options || {};
  options.remote = true;
  var session = new scout.Session($entryPoint, options);
  session.init();
  return session;
};

/**
 * @override
 */
scout.RemoteApp.prototype._init = function(options) {
  scout.RemoteApp.modifyWidgetPrototype();
  scout.RemoteApp.parent.prototype._init.call(this, options);
};

/**
 * Static method to modify the prototype of scout.Widget.
 */
scout.RemoteApp.modifyWidgetPrototype = function() {
  scout.Widget.prototype.createFromProperty = function(propertyName, value) {
    if (value instanceof scout.Widget) { // FIXME [awe] 6.1: check if this code is still required. In some cases (menu) we already have a Widget instance
      return value;
    }

    // Remote Case
    var remoteAdapter = findRemoteAdapter(this);
    if (remoteAdapter &&                                  // If the widget (or one of its parents) has a remote-adapter, all its properties must be remotable
        this._isPropertyRemotable(propertyName)) {        // True, if it's a 'managed' property of this remote-adapter
      return this.session.getOrCreateWidget(value, this); // value is a StringString, contains (remote) object ID
    }

    // Default: Local-Fall
    value.parent = this;
    return scout.create(value);

    function findRemoteAdapter(widget) {
      while (widget) {
        if (widget.remoteAdapter) {
          return widget.remoteAdapter;
        }
        widget = widget.parent;
      }
      return null;
    }
  };
};
