/* DocGen standalone UI runtime. No framework/network dependency is required. */
const Fragment = Symbol("Fragment");
function StrictMode(props) {
  return props.children;
}
const roots = new Set();
let currentInstance = null;
let hookIndex = 0;
let currentRoot = null;
let renderScheduled = false;
let currentContext = new Map();
const UNIT_LESS = new Set([
  "opacity",
  "zIndex",
  "fontWeight",
  "lineHeight",
  "flex",
  "flexGrow",
  "flexShrink",
  "order",
  "zoom",
  "scale",
  "tabSize",
  "columns",
  "orphans",
  "widows",
]);
const SVG_NAMESPACE = "http://www.w3.org/2000/svg";
const SVG_ATTRIBUTE_NAMES = {
  viewBox: "viewBox",
  preserveAspectRatio: "preserveAspectRatio",
  strokeWidth: "stroke-width",
  strokeLinecap: "stroke-linecap",
  strokeLinejoin: "stroke-linejoin",
  strokeDasharray: "stroke-dasharray",
  strokeDashoffset: "stroke-dashoffset",
  fillRule: "fill-rule",
  clipRule: "clip-rule",
  fillOpacity: "fill-opacity",
  strokeOpacity: "stroke-opacity",
};

function createElement(type, props, ...children) {
  props = props || {};
  if (children.length === 1) props.children = children[0];
  else if (children.length > 1) props.children = children;
  const key = props.key ?? null;
  if ("key" in props) {
    const copy = { ...props };
    delete copy.key;
    props = copy;
  }
  return { type, props, key };
}

function depsEqual(a, b) {
  if (a === undefined || b === undefined) return false;
  if (a === null || b === null) return a === b;
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) if (!Object.is(a[i], b[i])) return false;
  return true;
}
function assertHook() {
  if (!currentInstance)
    throw new Error("Hooks must be called from a component.");
  return currentInstance;
}
function useState(initial) {
  const inst = assertHook();
  const i = hookIndex++;
  if (!(i in inst.hooks))
    inst.hooks[i] = typeof initial === "function" ? initial() : initial;
  const setState = (value) => {
    const next = typeof value === "function" ? value(inst.hooks[i]) : value;
    if (Object.is(next, inst.hooks[i])) return;
    inst.hooks[i] = next;
    scheduleRender();
  };
  return [inst.hooks[i], setState];
}
function useRef(initial) {
  const inst = assertHook();
  const i = hookIndex++;
  if (!(i in inst.hooks)) inst.hooks[i] = { current: initial };
  return inst.hooks[i];
}
function useMemo(factory, deps) {
  const inst = assertHook();
  const i = hookIndex++;
  const old = inst.hooks[i];
  if (old && depsEqual(old.deps, deps)) return old.value;
  const value = factory();
  inst.hooks[i] = { value, deps };
  return value;
}
function useCallback(fn, deps) {
  return useMemo(() => fn, deps);
}
function useEffect(effect, deps) {
  const inst = assertHook();
  const i = hookIndex++;
  const old = inst.hooks[i];
  const changed = !old || !depsEqual(old.deps, deps);
  inst.hooks[i] = { deps, cleanup: old?.cleanup };
  if (changed)
    currentRoot.effects.push(() => {
      if (inst.hooks[i].cleanup) {
        try {
          inst.hooks[i].cleanup();
        } catch {}
      }
      const cleanup = effect();
      if (typeof cleanup === "function") inst.hooks[i].cleanup = cleanup;
    });
}
function useLayoutEffect(effect, deps) {
  return useEffect(effect, deps);
}
function useReducer(reducer, initialArg, init) {
  const [state, setState] = useState(() =>
    init ? init(initialArg) : initialArg,
  );
  const dispatch = (action) => setState((s) => reducer(s, action));
  return [state, dispatch];
}
function useTransition() {
  const [pending, setPending] = useState(false);
  const startTransition = (fn) => {
    setPending(true);
    try {
      fn();
    } finally {
      queueMicrotask(() => setPending(false));
    }
  };
  return [pending, startTransition];
}
function createContext(defaultValue) {
  const ctx = { defaultValue, current: defaultValue, Provider: null };
  ctx.Provider = function Provider(props) {
    return props.children;
  };
  ctx.Provider.__context = ctx;
  return ctx;
}
function useContext(ctx) {
  const i = assertHook();
  hookIndex++;
  return currentContext.has(ctx) ? currentContext.get(ctx) : ctx.defaultValue;
}

function setStyle(el, style, oldStyle = {}) {
  style = style || {};
  for (const k of Object.keys(oldStyle)) {
    if (k in style && style[k] != null) continue;
    try {
      el.style[k] = "";
      el.style.removeProperty(k.replace(/[A-Z]/g, (m) => `-${m.toLowerCase()}`));
    } catch {}
  }
  for (const [k, v] of Object.entries(style)) {
    if (v == null) continue;
    const value =
      typeof v === "number" && !UNIT_LESS.has(k) && !k.startsWith("--")
        ? `${v}px`
        : String(v);
    try {
      el.style[k] = value;
    } catch {
      try {
        el.style.setProperty(
          k.replace(/[A-Z]/g, (m) => `-${m.toLowerCase()}`),
          value,
        );
      } catch {}
    }
  }
}
function setProp(el, name, value) {
  if (name.startsWith("on")) {
    const event = name.slice(2).toLowerCase();
    if (!el.__runtimeHandlers) el.__runtimeHandlers = Object.create(null);
    if (!el.__runtimeHandlers[event]) {
      el.__runtimeHandlers[event] = null;
      el.addEventListener(event, (e) => {
        const handler = el.__runtimeHandlers[event];
        if (handler) handler.call(el, e);
      });
    }
    el.__runtimeHandlers[event] =
      typeof value === "function" ? value : null;
    return;
  }
  if (
    name === "children" ||
    name === "key" ||
    name === "ref" ||
    name === "dangerouslySetInnerHTML"
  )
    return;
  if (name === "className") {
    if (el.namespaceURI === SVG_NAMESPACE)
      el.setAttribute("class", value || "");
    else el.className = value || "";
    return;
  }
  if (name === "style") {
    setStyle(el, value);
    return;
  }
  if (
    name === "checked" ||
    name === "selected" ||
    name === "disabled" ||
    name === "multiple" ||
    name === "readOnly" ||
    name === "required"
  ) {
    el[name] = !!value;
    if (value) el.setAttribute(name, "");
    else el.removeAttribute(name);
    return;
  }
  if (name === "value" && "value" in el) {
    const nextValue = value == null ? "" : value;
    if (el.value !== nextValue) el.value = nextValue;
    return;
  }
  if (value === false || value == null) {
    el.removeAttribute(name);
    return;
  }
  if (value === true) {
    el.setAttribute(name, "");
    return;
  }
  const isSvg = el.namespaceURI === SVG_NAMESPACE;
  const attr = isSvg
    ? SVG_ATTRIBUTE_NAMES[name] || name
    : name.replace(/[A-Z]/g, (m) => `-${m.toLowerCase()}`);
  try {
    el.setAttribute(attr, String(value));
  } catch {}
}
function childrenOf(value) {
  if (value == null || value === false || value === true) return [];
  return Array.isArray(value) ? value : [value];
}
function cleanupInstance(inst) {
  for (const hook of inst.hooks) {
    if (typeof hook?.cleanup !== "function") continue;
    const cleanup = hook.cleanup;
    hook.cleanup = null;
    try {
      cleanup();
    } catch (error) {
      setTimeout(() => {
        throw error;
      }, 0);
    }
  }
}
function patchProps(el, oldProps, props) {
  const names = new Set([...Object.keys(oldProps), ...Object.keys(props)]);
  for (const name of names) {
    if (name === "children" || name === "key") continue;
    const oldValue = oldProps[name];
    const value = props[name];
    if (Object.is(oldValue, value)) continue;
    if (name === "style") setStyle(el, value, oldValue);
    else setProp(el, name, value);
  }
}
function syncChildren(parent, children) {
  const desired = new Set(children);
  for (const child of Array.from(parent.childNodes)) {
    if (!desired.has(child)) parent.removeChild(child);
  }
  let cursor = parent.firstChild;
  for (const child of children) {
    if (child !== cursor) parent.insertBefore(child, cursor);
    cursor = child.nextSibling;
  }
}
function renderNode(vnode, parent, path, ctx) {
  if (vnode == null || vnode === false || vnode === true) return [];
  if (typeof vnode === "string" || typeof vnode === "number") {
    const old = currentRoot.domNodes.get(path);
    const node = old?.kind === "text" ? old.dom : document.createTextNode("");
    const text = String(vnode);
    if (node.nodeValue !== text) node.nodeValue = text;
    currentRoot.nextDomNodes.set(path, { kind: "text", dom: node });
    return [node];
  }
  if (Array.isArray(vnode)) {
    return vnode.flatMap((v, i) =>
      renderNode(v, parent, `${path}.${v?.key ?? i}`, ctx),
    );
  }
  const { type, props = {}, key } = vnode;
  if (type === Fragment) {
    return childrenOf(props.children).flatMap((v, i) =>
      renderNode(v, parent, `${path}.f${v?.key ?? i}`, ctx),
    );
  }
  if (type && type.__context) {
    const next = new Map(ctx);
    next.set(type.__context, props.value);
    return childrenOf(props.children).flatMap((v, i) =>
      renderNode(v, parent, `${path}.p${v?.key ?? i}`, next),
    );
  }
  if (typeof type === "function") {
    const instanceKey = `${path}|${type.name || "Component"}`;
    let inst = currentRoot.instances.get(instanceKey);
    if (!inst) {
      inst = { hooks: [] };
      currentRoot.instances.set(instanceKey, inst);
    }
    currentRoot.renderInstances.add(instanceKey);
    const prevInst = currentInstance,
      prevIndex = hookIndex,
      prevCtx = currentContext;
    currentInstance = inst;
    hookIndex = 0;
    currentContext = ctx;
    let child;
    try {
      child = type(props);
    } catch (error) {
      currentRoot.error = error;
      child = {
        type: "div",
        props: {
          style: { padding: 24, color: "#b91c1c" },
          children: `UI error: ${error.message || error}`,
        },
      };
    }
    currentInstance = prevInst;
    hookIndex = prevIndex;
    currentContext = prevCtx;
    return renderNode(child, parent, path + ".c", ctx);
  }
  if (typeof type !== "string") return [];
  const old = currentRoot.domNodes.get(path);
  const compatible =
    old?.kind === "element" && old.type === type && old.key === key;
  const isSvg = type === "svg" || parent.namespaceURI === SVG_NAMESPACE;
  const el = compatible
    ? old.dom
    : isSvg
      ? document.createElementNS(SVG_NAMESPACE, type)
      : document.createElement(type);
  patchProps(el, compatible ? old.props : {}, props);
  currentRoot.nextDomNodes.set(path, {
    kind: "element",
    type,
    key,
    dom: el,
    props,
  });
  if (props.dangerouslySetInnerHTML?.__html != null) {
    const html = props.dangerouslySetInnerHTML.__html;
    if (!compatible || old.props.dangerouslySetInnerHTML?.__html !== html)
      el.innerHTML = html;
  } else {
    const children = childrenOf(props.children).flatMap((v, i) =>
      renderNode(v, el, `${path}.${v?.key ?? i}`, ctx),
    );
    syncChildren(el, children);
  }
  return [el];
}
function cleanupUnmountedInstances(root) {
  for (const [key, inst] of root.instances) {
    if (root.renderInstances.has(key)) continue;
    cleanupInstance(inst);
    root.instances.delete(key);
  }
}
function setContextForRender(root) {
  root.renderInstances = new Set();
  root.nextDomNodes = new Map();
}
function flush() {
  renderScheduled = false;
  if (!currentRoot) return;
  const root = currentRoot;
  root.effects = [];
  root.error = null;
  setContextForRender(root);
  const children = renderNode(root.element, root.container, "0", new Map());
  syncChildren(root.container, children);
  root.domNodes = root.nextDomNodes;
  cleanupUnmountedInstances(root);
  const effects = root.effects.slice();
  root.effects = [];
  effects.forEach((run) => {
    try {
      run();
    } catch (error) {
      setTimeout(() => {
        throw error;
      }, 0);
    }
  });
}
function scheduleRender() {
  if (renderScheduled) return;
  renderScheduled = true;
  queueMicrotask(flush);
}
function createRoot(container) {
  const root = {
    container,
    element: null,
    instances: new Map(),
    domNodes: new Map(),
    effects: [],
    error: null,
  };
  roots.add(root);
  currentRoot = root;
  return {
    render(element) {
      root.element = element;
      currentRoot = root;
      flush();
    },
    unmount() {
      root.container.replaceChildren();
      for (const inst of root.instances.values()) cleanupInstance(inst);
      root.instances.clear();
      root.domNodes.clear();
      roots.delete(root);
      if (currentRoot === root) currentRoot = null;
    },
  };
}
export {
  Fragment,
  StrictMode,
  createElement,
  createContext,
  useState,
  useRef,
  useMemo,
  useCallback,
  useEffect,
  useLayoutEffect,
  useReducer,
  useTransition,
  useContext,
  createRoot,
};
export default {
  Fragment,
  StrictMode,
  createElement,
  createContext,
  useState,
  useRef,
  useMemo,
  useCallback,
  useEffect,
  useLayoutEffect,
  useReducer,
  useTransition,
  useContext,
};
