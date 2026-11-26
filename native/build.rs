use std::env;

fn pass_env(key: &str, default: &str) {
    println!("cargo:rerun-if-env-changed={key}");
    let val = env::var(key)
        .ok()
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())
        .unwrap_or_else(|| default.to_string());
    println!("cargo:rustc-env={key}={val}");
}

fn main() {
    pass_env("RC_CORE_VERSION", "0.1.0");
    pass_env("RC_CORE_GIT_SHA", "unknown");
    pass_env("RC_CORE_ABI_VERSION", "1");
    pass_env("RC_CORE_BUILT_AT", "");
}
