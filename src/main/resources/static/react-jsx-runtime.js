import { createElement, Fragment } from './react.js';
export { Fragment };
export function jsx(type, props, key){ return createElement(type,{...(props||{}), ...(key!=null?{key}: {})}); }
export function jsxs(type, props, key){ return createElement(type,{...(props||{}), ...(key!=null?{key}: {})}); }
export const jsxDEV=jsx;
