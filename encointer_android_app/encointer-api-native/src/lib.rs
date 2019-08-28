#![cfg(target_os="android")]
#![allow(non_snake_case)]
extern crate jni;
extern crate substrate_api_client;

use std::ffi::{CString, CStr};
use jni::JNIEnv;
use jni::objects::{JObject, JString};
use jni::sys::{jstring};

use substrate_api_client::{
    Api,
    compose_extrinsic,
    crypto::{AccountKey, CryptoKind},
    extrinsic,
};

#[no_mangle]
pub unsafe extern fn Java_com_encointer_signer_EncointerActivity_send_xt(env: JNIEnv, _: JObject, j_url: JString) -> jstring {
    let url = CString::from(
        CStr::from_ptr(
            env.get_string(j_url).unwrap().as_ptr()
        )
    );

    env_logger::init();

    // initialize api and set the signer (sender) that is used to sign the extrinsics
    let from = AccountKey::new("//Alice", Some(""), CryptoKind::Sr25519);
    let api = Api::new(format!("ws://{:?}", url))
        .set_signer(from);

    println!("[+] Alice's Account Nonce is {}\n", api.get_nonce().unwrap());
    let to = AccountKey::public_from_suri("//Bob", Some(""), CryptoKind::Sr25519);

    // Exchange "Balance" and "transfer" with the names of your custom runtime module. They are only
    // used here to be able to run the examples against a generic substrate node with standard modules.
    let xt = compose_extrinsic!(
        api.clone(),
        "Balances",
        "transfer",
        GenericAddress::from(to),
        Compact(42 as u128)
    );

    println!("[+] Composed Extrinsic:\n {:?}\n", xt);

    //send and watch extrinsic until finalized
    let tx_hash = api.send_extrinsic(xt.hex_encode()).unwrap();
    println!("[+] Transaction got finalized. Hash: {:?}", tx_hash);
    
    let output = env.new_string("xt finalized: ".to_owned() + &tx_hash.to_string()).unwrap();
    output.into_inner()
}