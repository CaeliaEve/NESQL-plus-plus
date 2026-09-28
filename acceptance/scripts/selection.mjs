import assert from 'node:assert/strict';

export function selection(state, phase, ids) {
  assert.ok(['data', 'visuals', 'magic'].includes(phase), 'Choose data, visuals or magic');
  const available = state.handlers.filter(handler => handler.supported);
  let handlers;
  if (ids !== undefined) {
    const requested = ids.split(',');
    assert.ok(requested.length && new Set(requested).size === requested.length, 'Handler ids must be unique');
    handlers = requested.map(id => {
      assert.match(id, /^category_[a-f0-9]{64}$/);
      const handler = available.find(handler => handler.id === id);
      assert.ok(handler, 'Requested handler is missing or unsupported: ' + id);
      return handler;
    });
  } else if (phase === 'magic') handlers = available.filter(handler => handler.source.handler.startsWith('ru.timeconqueror.tcneiadditions.nei.'));
  else {
    const furnace = available.find(handler => handler.source.handler === 'codechicken.nei.recipe.FurnaceRecipeHandler');
    const machines = available.filter(handler => handler.source.handler === 'gregtech.nei.GTNEIDefaultHandler');
    const machine = machines.find(handler => handler.source.key === 'gt.recipe.macerator/gt.recipe.macerator')
      ?? machines.find(handler => /macerat/i.test(handler.source.key)) ?? machines[0];
    assert.ok(furnace && machine, 'The acceptance run needs the furnace and one GT handler');
    handlers = [furnace, machine];
  }
  assert.ok(handlers.length > 0, 'An empty selection would request all handlers');
  return { phase, scope: 'selection', complete: false, selected: handlers, registered: state.handlers.length,
    supported: available.length, unsupported: state.handlers.filter(handler => !handler.supported) };
}
