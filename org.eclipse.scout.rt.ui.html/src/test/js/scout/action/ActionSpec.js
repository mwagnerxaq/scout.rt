describe('Action', function() {

  var $sandbox, session,
    action = new scout.Action();

  beforeEach(function() {
    setFixtures(sandbox());
    session = sandboxSession();
    $sandbox = $('#sandbox');
    action.session = session;
  });

  describe('defaults', function() {

    it('should be as expected', function() {
      expect(action.tabbable).toBe(false);
      expect(action.actionStyle).toBe(scout.Action.ActionStyle.DEFAULT);
    });

  });

  describe('setTabbable', function() {

    it('should modify $container tabindex', function() {
      // because Action is 'abstract' and has no _render method yet
      // but _renderProperties() is called anyway
      action.$container = $sandbox;
      action.render($sandbox);
      expect(action.$container.attr('tabindex')).toBe(undefined);

      action.setTabbable(true);
      expect(action.$container.attr('tabindex')).toBe('0');
    });

  });


});
