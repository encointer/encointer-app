//  Copyright (c) 2019 Alain Brenzikofer
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.


#![cfg(target_os="android")]
#![allow(non_snake_case)]
extern crate jni;
extern crate substrate_api_client;
#[macro_use] extern crate log;
extern crate android_logger;

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

use log::Level;
use android_logger::Config;



#[no_mangle]
pub unsafe extern fn Java_com_encointer_signer_NativeApiThread_sendxt(env: JNIEnv, _: JObject, j_url: JString) -> jstring {
    let url = CString::from(
        CStr::from_ptr(
            env.get_string(j_url).unwrap().as_ptr()
        )
    );

    android_logger::init_once(
        Config::default()
            .with_min_level(Level::Trace) // limit log level
            .with_tag("encointer-api-native") // logs will show under mytag tag
    );
    info!("called into native sendxt");

    info!("connecting to {}", url.clone().into_string().unwrap());


    // initialize api and set the signer (sender) that is used to sign the extrinsics
    let from = AccountKey::new("//Alice", Some(""), CryptoKind::Sr25519);
    let api = Api::new(format!("ws://{}", url.into_string().unwrap()))
        .set_signer(from);

    info!("[+] Alice's Account Nonce is {}\n", api.get_nonce().unwrap());
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

    info!("[+] Composed Extrinsic:\n {:?}\n", xt);

    //send and watch extrinsic until finalized
    let tx_hash = api.send_extrinsic(xt.hex_encode()).unwrap();
    info!("[+] Transaction got finalized. Hash: {:?}", tx_hash);
    
    let output = env.new_string("xt finalized: ".to_owned() + &tx_hash.to_string()).unwrap();
    output.into_inner()
}