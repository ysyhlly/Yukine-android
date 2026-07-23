#![cfg_attr(not(target_os = "android"), allow(dead_code))]
#![deny(unsafe_op_in_unsafe_fn)]

mod protocol;

#[cfg(target_os = "android")]
mod android;
