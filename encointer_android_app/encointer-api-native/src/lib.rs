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
#[macro_use] extern crate json;


use std::ffi::{CString, CStr};
use jni::JNIEnv;
use jni::objects::{JObject, JString};
use jni::sys::{jstring};

use substrate_api_client::{
    Api,
    compose_extrinsic_offline,
    crypto::{AccountKey, CryptoKind},
    extrinsic,
    rpc::json_req,
    utils::{storage_key_hash, hexstr_to_hash},
};
use codec::{Encode, Decode};
use primitives::{
	crypto::{set_default_ss58_version, Ss58AddressFormat, Ss58Codec},
	ed25519, sr25519, Pair, Public, H256, hexdisplay::HexDisplay,
};
use bip39::{Mnemonic, Language, MnemonicType};
use log::Level;
use android_logger::Config;
use oping::{Ping, PingResult};

use encointer_node_runtime::{Call, EncointerCeremoniesCall, Signature,
    encointer_ceremonies::{ClaimOfAttendance, Witness, CeremonyIndexType,
        MeetupIndexType}
}; 

#[no_mangle]
pub unsafe extern fn Java_com_encointer_signer_EncointerActivity_newAccount(env: JNIEnv, _: JObject) -> jstring {
 
    android_logger::init_once(
        Config::default()
            .with_min_level(Level::Trace) // limit log level
            .with_tag("encointer-api-native") // logs will show under mytag tag
    );
    info!("called into native newaccount");
    let mnemonic = Mnemonic::new(MnemonicType::Words12, Language::English);
    info!("newaccount phrase: {}", mnemonic.phrase());
    let newpair = sr25519::Pair::from_phrase(mnemonic.phrase(), None);
    info!("newaccount address (ss58): {}", newpair.public().to_ss58check());
    let outputjson = object!{
        "phrase" => mnemonic.phrase(),
        "address" => newpair.public().to_ss58check(),
        "pair" => hex::encode(&newpair.encode())
    };
    let output = env.new_string(outputjson).unwrap();
    output.into_inner()
}

#[no_mangle]
pub unsafe extern fn Java_com_encointer_signer_EncointerActivity_newClaim(env: JNIEnv, _: JObject, j_arg: JString) -> jstring {
    android_logger::init_once(
        Config::default()
            .with_min_level(Level::Trace) // limit log level
            .with_tag("encointer-api-native")); // logs will show under mytag tag

    let arg = json::parse(CString::from(
        CStr::from_ptr(
            env.get_string(j_arg).unwrap().as_ptr())));

    let pair: Option<sr25519::Pair> = match hex::decode(arg["pair"]) {
        Ok(p) => Some(p.decode()),
        Err(_) => None,
    };
    let cindex: Option<CeremonyIndexType> = match hex::decode(arg["ceremony_index"]) {
        Ok(x) => Some(x.decode()),
        Err(_) => None,
    };
    let mindex: Option<MeetupIndexType> = match hex::decode(arg["meetup_index"]) {
        Ok(x) => Some(x.decode()),
        Err(_) => None,
    };
    let n_participans: Option<CeremonyIndexType> = match hex::decode(arg["n_participants"]) {
        Ok(x) => Some(x.decode()),
        Err(_) => None,
    };

    if pair.is_none() { return env.new_string("ERROR: must specify pair"); }
    if cindex.is_none() { return env.new_string("ERROR: must specify ceremony index"); }
    if mindex.is_none() { return env.new_string("ERROR: must specify ceremony index"); }
    if n_participants.is_none() { return env.new_string("ERROR: must specify ceremony index"); }

    let claim = ClaimOfAttendance {
		claimant_public: pair.unwrap().public().into(),
        ceremony_index: cindex.unwrap(),
        meetup_index: mindex.unwrap(),
        number_of_participants_confirmed: n_participants.unwrap(),
	};
    let output = env.new_string(hex::encode(claim.encode())).unwrap();
    output.into_inner()
}

#[no_mangle]
pub unsafe extern fn Java_com_encointer_signer_EncointerActivity_signClaim(env: JNIEnv, _: JObject, j_arg: JString) -> jstring {
    android_logger::init_once(
        Config::default()
            .with_min_level(Level::Trace) // limit log level
            .with_tag("encointer-api-native")); // logs will show under mytag tag

    let arg = json::parse(CString::from(
        CStr::from_ptr(
            env.get_string(j_arg).unwrap().as_ptr())));

    let pair: Option<sr25519::Pair> = match hex::decode(arg["pair"]) {
        Ok(p) => Some(p.decode()),
        Err(_) => None,
    };
    let claim: Option<claim> = match hex::decode(arg["claim"]) {
        Ok(c) => Some(c.decode()),
        Err(_) => None,
    };
    if pair.is_none() { return env.new_string("ERROR: must specify pair"); }
    if claim.is_none() { return env.new_string("ERROR: must specify claim"); }
    let witness = Witness { 
        claim: claim.unwrap().clone(),
        signature: Signature::from(pair.unwrap().sign(&claim.encode())),
        public: pair.unwrap().public().into(),
	};
    let output = env.new_string(hex::encode(witness.encode())).unwrap();
    output.into_inner()    
}

#[no_mangle]
pub unsafe extern fn Java_com_encointer_signer_EncointerActivity_getJsonReq(env: JNIEnv, _: JObject, j_request: JString, j_arg: JString) -> jstring {
    android_logger::init_once(
        Config::default()
            .with_min_level(Level::Trace) // limit log level
            .with_tag("encointer-api-native")); // logs will show under mytag tag

    let request = CString::from(
        CStr::from_ptr(
            env.get_string(j_request).unwrap().as_ptr()));

    let arg = json::parse(CString::from(
        CStr::from_ptr(
            env.get_string(j_arg).unwrap().as_ptr())));

    let pair: Option<sr25519::Pair> = match hex::decode(arg["pair"]) {
        Ok(p) => Some(p.decode()),
        Err(_) => None,
    };
    let genesis_hash: Option<Hash> = match hexstr_to_hash(arg["genesis_hash"]) {
        Ok(h) => Some(h),
        Err(_) => None,
    };
    let spec_version: Option<Hash> = match arg["spec_version"] {
        json::Null => None,
        any => Some(any),
    };
    let nonce: Option<u32> = match arg["nonce"] {
        json::Null => None,
        any => Some(any.into()),
    };
    let witnesses: Option<Vec<Witness>> = match arg["witnesses"] {
        json::Null => None,
        any => any.iter().map(|x| hex::decode(x).unwrap().decode().unwrap()),
    };
    let jsonreq = match request {
        "subscribe_events" => {
            let key = storage_key_hash("System", "Events", None);
            json_req::state_subscribe_storage(&key).to_string()
        }
        "subscribe_balance_for" => {
            if pair.is_none() { "ERROR: must specify pair".to_string() }
            let key = storage_key_hash("Balances", "FreeBalances", AccountId::from(pair.unwrap()).encode());
            json_req::state_subscribe_storage(&key).to_string()
        }
        "subscribe_nonce_for" => {
            if pair.is_none() { "ERROR: must specify pair".to_string() }
            let key = storage_key_hash("System", "AccountNone", AccountId::from(pair.unwrap()).encode());
            json_req::state_subscribe_storage(&key).to_string()
        },
        "get_meetup_index_for" => {
            if pair.is_none() { "ERROR: must specify pair".to_string() }
            // FIXME: need to implement double_map for api-client first.
            let key = storage_key_hash("EncointerCeremonies", "MeetupIndex", AccountId::from(pair.unwrap()).encode());
            json_req::state_get_storage(&key).to_string()
        },
        "get_ceremony_index" => {
            let key = storage_key_hash("EncointerCeremonies", "CurrentCeremonyIndex", None);
            json_req::state_get_storage(&key).to_string()
        },
        "get_runtime_version" => {
            json_req::state_get_runtime_version().to_string()
        },
        "get_genesis_hash" => {
            json_req::chain_get_block_hash().to_string()
        },
        "bootstrap_funding" => {
            // TODO to be replaced by faucet
            if pair.is_none() { "ERROR: must specify pair".to_string() }
            if nonce.is_none() { "ERROR: must specify nonce".to_string() }
            if genesis_hash.is_none() { "ERROR: must specify genesis hash".to_string() }
            if spec_version.is_none() { "ERROR: must specify spec version".to_string() }
            let from = AccountKeyring::Alice.pair();
            let to = AccountId::from(pair);
            let xt: UncheckedExtrinsicV3<_, sr25519::Pair> = compose_extrinsic_offline!(
                from,
                Call::Balances(BalancesCall::transfer(to.clone().into(), 42)),
                nonce.unwrap(),
                genesis_hash.unwrap(),
                spec_version.unwrap()
            );
            let jsonreq = json_req::author_submit_and_watch_extrinsic(&xt).to_string();
        },
        "register_participant" => {
            if pair.is_none() { "ERROR: must specify pair".to_string() }
            if nonce.is_none() { "ERROR: must specify nonce".to_string() }
            if genesis_hash.is_none() { "ERROR: must specify genesis hash".to_string() }
            if spec_version.is_none() { "ERROR: must specify spec version".to_string() } 
            let xt: UncheckedExtrinsicV3<_, sr25519::Pair> = compose_extrinsic_offline!(
                pair.unwrap(),
                Call::EncointerCeremonies(EncointerCeremoniesCall::register_participant()),
                nonce.unwrap(),
                genesis_hash.unwrap(),
                spec_version.unwrap()
            );
            let jsonreq = json_req::author_submit_and_watch_extrinsic(&xt).to_string();
        },
         "register_witnesses" => {
            if pair.is_none() { "ERROR: must specify pair".to_string() }
            if nonce.is_none() { "ERROR: must specify nonce".to_string() }
            if genesis_hash.is_none() { "ERROR: must specify genesis hash".to_string() }
            if spec_version.is_none() { "ERROR: must specify spec version".to_string() } 
            if witnesses.is_none() { "ERROR: must specify witnesses".to_string() } 
            
            let xt: UncheckedExtrinsicV3<_, sr25519::Pair> = compose_extrinsic_offline!(
                pair.unwrap(),
                Call::EncointerCeremonies(EncointerCeremoniesCall::register_witnesses(witnesses.unwrap())),
                nonce.unwrap(),
                genesis_hash.unwrap(),
                spec_version.unwrap(),
            );
            let jsonreq = json_req::author_submit_and_watch_extrinsic(&xt).to_string();
        },
       
    };
    
    let output = env.new_string(jsonreq).unwrap();
    output.into_inner()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn it_works() {
        let req = new_account();
    }
}
