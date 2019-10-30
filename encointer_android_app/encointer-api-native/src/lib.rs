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
use keyring::AccountKeyring;
use substrate_api_client::{
    Api,
    compose_extrinsic_offline,
    extrinsic, 
    extrinsic::xt_primitives::{AccountId, UncheckedExtrinsicV3, 
        GenericExtra, SignedPayload, GenericAddress},
    rpc::json_req,
    utils::{storage_key_hash, storage_key_hash_double_map, hexstr_to_hash},
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

use encointer_node_runtime::{Call, EncointerCeremoniesCall, BalancesCall, 
    Signature, Hash,
    encointer_ceremonies::{ClaimOfAttendance, Witness, CeremonyIndexType,
        MeetupIndexType}
}; 
use serde_json;

static RUNTIME_EXCEPTION_CLASS: &str = "java/lang/RuntimeException";

macro_rules! unwrap_or_return { ( $obj:expr, $env:expr, $err:literal ) => { 
    match $obj { 
        None => { 
            error!($err);
            let output = $env.new_string($err).unwrap();
            return output.into_inner();
        },
        Some(x) => x
    } 
}} 

macro_rules! parse_json_j { ( $obj:expr, $env:expr ) => { 
    match json::parse(CStr::from_ptr(
        $env.get_string($obj).unwrap().as_ptr()).to_str().unwrap()) {
            Ok(j) => j,
            _ => { 
                let output = $env.new_string("error parsing arg json").unwrap();
                return output.into_inner();
            },
    }
}} 


#[no_mangle]
pub unsafe extern fn Java_com_encointer_signer_EncointerActivity_initNativeLogger(env: JNIEnv, _: JObject) {
    android_logger::init_once(
        Config::default()
            .with_min_level(Level::Trace) // limit log level
            .with_tag("encointer-api-native") // logs will show under mytag tag
    );
    info!("native logger initialized");
} 

#[no_mangle]
pub unsafe extern fn Java_com_encointer_signer_EncointerActivity_mustThrowException(env: JNIEnv, _: JObject) {
    env.throw_new(RUNTIME_EXCEPTION_CLASS, "always throwing error for testing").unwrap(); 
}

#[no_mangle]
pub unsafe extern fn Java_com_encointer_signer_EncointerActivity_newAccount(env: JNIEnv, _: JObject) -> jstring {
    info!("called into native newaccount");
    let mnemonic = Mnemonic::new(MnemonicType::Words12, Language::English);
    info!("newaccount phrase: {}", mnemonic.phrase());
    let newpair = sr25519::Pair::from_phrase(mnemonic.phrase(), None).unwrap().0;
    info!("newaccount address (ss58): {}", newpair.public().to_ss58check());
    let outputjson = object!{
        "phrase" => mnemonic.phrase(),
        "address" => newpair.public().to_ss58check()
    };
    let output = env.new_string(outputjson.dump()).unwrap();
    output.into_inner()
}

#[no_mangle]
pub unsafe extern fn Java_com_encointer_signer_EncointerActivity_newClaim(env: JNIEnv, _: JObject, j_arg: JString) -> jstring {
    let arg = parse_json_j!(j_arg, env);
    info!("newClaim called with args {}", arg.dump());
    let pair: Option<sr25519::Pair> = match arg["phrase"].as_str() {
        Some(p) => Some(sr25519::Pair::from_phrase(p, None).unwrap().0),
        None => None,
    };
    let cindex: Option<CeremonyIndexType> = match arg["ceremony_index"].as_str() {
        Some(x) => Some(Decode::decode(&mut &hex::decode(x).unwrap()[..]).unwrap()),
        None => None,
    };
    let mindex: Option<MeetupIndexType> = match arg["meetup_index"].as_str() {
        Some(x) => Some(Decode::decode(&mut &hex::decode(x).unwrap()[..]).unwrap()),
        None => None,
    };
    let n_participants: Option<CeremonyIndexType> = match arg["n_participants"].as_str() {
        Some(x) => Some(Decode::decode(&mut &hex::decode(x).unwrap()[..]).unwrap()),
        None => None,
    };
    let pair = pair.expect("pair is specified");
    let cindex = cindex.expect("ceremony index is specified");
    let mindex = mindex.expect("meetup index is specified");
    let n_participants = n_participants.expect("n participants is specified");

    let claim = ClaimOfAttendance::<AccountId> {
		claimant_public: pair.public().into(),
        ceremony_index: cindex,
        meetup_index: mindex,
        number_of_participants_confirmed: n_participants,
	};
    let output = env.new_string(hex::encode(claim.encode())).unwrap();
    output.into_inner()
}

#[no_mangle]
pub unsafe extern fn Java_com_encointer_signer_EncointerActivity_signClaim(env: JNIEnv, _: JObject, j_arg: JString) -> jstring {
    info!("signClaim called");
    let arg = parse_json_j!(j_arg, env);
    info!("signClaim called with args {}", arg.dump());
    let pair: Option<sr25519::Pair> = match arg["phrase"].as_str() {
        Some(p) => Some(sr25519::Pair::from_phrase(p, None).unwrap().0),
        None => None,
    };
    let claim: Option<ClaimOfAttendance<AccountId>> = match arg["claim"].as_str() {
        Some(c) => Some(Decode::decode(&mut &hex::decode(c).unwrap()[..]).unwrap()),
        None => None,
    };
    let pair = pair.expect("pair is specified");
    let claim = claim.expect("claim is specified");
    let witness = Witness { 
        claim: claim.clone(),
        signature: Signature::from(pair.sign(&claim.encode())),
        public: pair.public().into(),
	};
    let output = env.new_string(hex::encode(witness.encode())).unwrap();
    output.into_inner()    
}

#[no_mangle]
pub unsafe extern fn Java_com_encointer_signer_EncointerActivity_getJsonReq(env: JNIEnv, _: JObject, j_request: JString, j_arg: JString) -> jstring {
    info!("getJsonReq called");
    let request = CStr::from_ptr(
        env.get_string(j_request).unwrap().as_ptr()).to_string_lossy().into_owned();
    info!("getJsonReq called with request {}", request);
    let arg = parse_json_j!(j_arg, env);
    info!("getJsonReq called with args {}", arg.dump());
    let pair: Option<sr25519::Pair> = match arg["phrase"].as_str() {
        Some(p) => Some(sr25519::Pair::from_phrase(p, None).unwrap().0),
        None => None,
    };
    let genesis_hash: Option<Hash> = match arg["genesis_hash"].as_str() {
        Some(h) => Some(hexstr_to_hash(h.to_string()).unwrap()),
        None => None,
    };        
    let spec_version: Option<u32> = arg["spec_version"].as_u32();
    let nonce: Option<u32> = arg["nonce"].as_u32();
    let cindex: Option<CeremonyIndexType> = arg["cindex"].as_u32();
    let witnesses: Option<Vec<Witness<Signature, AccountId>>> = match &arg["witnesses"] {
        json::JsonValue::Array(w) => Some(w.iter().map(|x| 
            Decode::decode(&mut &hex::decode((*x).as_str().unwrap()).unwrap()[..])
            .unwrap()).collect()),
        _ => None,
    };
    let id = match arg["id"].as_u32() {
        Some(n) => n,
        _ => 42,
    };
    info!("getJsonReq: matching request: {}", request);
    let jsonreq = match request.as_str() {
        "subscribe_events" => {
            let key = storage_key_hash("System", "Events", None);
            json_req::state_subscribe_storage_with_id(&key, id)
        }
        "subscribe_balance_for" => {
            let pair = unwrap_or_return!(pair, env, "pair has to be specified");
            let key = storage_key_hash("Balances", "FreeBalance", Some(AccountId::from(pair.public()).encode()));
            json_req::state_subscribe_storage_with_id(&key, id)
        }
        "subscribe_nonce_for" => {
            let pair = unwrap_or_return!(pair, env, "pair has to be specified");
            let key = storage_key_hash("System", "AccountNone", Some(AccountId::from(pair.public()).encode()));
            json_req::state_subscribe_storage_with_id(&key, id)
        },
        "subscribe_ceremony_index" => {
            let key = storage_key_hash("EncointerCeremonies", "CurrentCeremonyIndex", None);
            json_req::state_subscribe_storage_with_id(&key, id)
        },
        "subscribe_ceremony_phase" => {
            let key = storage_key_hash("EncointerCeremonies", "CurrentPhase", None);
            json_req::state_subscribe_storage_with_id(&key, id)
        },
        "get_meetup_index_for" => {
            let pair = unwrap_or_return!(pair, env, "pair has to be specified");
            let cindex = unwrap_or_return!(cindex, env, "ceremony index has to be specified");
            let key = storage_key_hash_double_map("EncointerCeremonies", "MeetupIndex", cindex.encode(), AccountId::from(pair.public()).encode());
            json_req::state_get_storage_with_id(&key, id)
        },
        "get_runtime_version" => {
            json_req::state_get_runtime_version_with_id(id)
        },
        "get_genesis_hash" => {
            json_req::chain_get_block_hash_with_id(id)
        },
        "bootstrap_funding" => {
            // TODO to be replaced by faucet
            let pair = unwrap_or_return!(pair, env, "pair has to be specified");
            let nonce = unwrap_or_return!(nonce, env, "nonce has to be specified");
            let genesis_hash = unwrap_or_return!(genesis_hash, env, "genesis_hash has to be specified");
            let spec_version = unwrap_or_return!(spec_version, env, "spec_version has to be specified");
            let from = AccountKeyring::Alice.pair();
            let to = AccountId::from(pair.public());
            let xt: UncheckedExtrinsicV3<_, sr25519::Pair> = compose_extrinsic_offline!(
                from,
                Call::Balances(BalancesCall::transfer(to.clone().into(), 42)),
                nonce,
                genesis_hash,
                spec_version
            );
            json_req::author_submit_and_watch_extrinsic_with_id(&xt.hex_encode(), id)
        },
        "register_participant" => {
            let pair = unwrap_or_return!(pair, env, "pair has to be specified");
            let nonce = unwrap_or_return!(nonce, env, "nonce has to be specified");
            let genesis_hash = unwrap_or_return!(genesis_hash, env, "genesis_hash has to be specified");
            let spec_version = unwrap_or_return!(spec_version, env, "spec_version has to be specified");
            let xt: UncheckedExtrinsicV3<_, sr25519::Pair> = compose_extrinsic_offline!(
                pair,
                Call::EncointerCeremonies(EncointerCeremoniesCall::register_participant()),
                nonce,
                genesis_hash,
                spec_version
            );
            json_req::author_submit_and_watch_extrinsic_with_id(&xt.hex_encode(),id)
        },
        "register_witnesses" => {
            let pair = unwrap_or_return!(pair, env, "pair has to be specified");
            let nonce = unwrap_or_return!(nonce, env, "nonce has to be specified");
            let genesis_hash = unwrap_or_return!(genesis_hash, env, "genesis_hash has to be specified");
            let spec_version = unwrap_or_return!(spec_version, env, "spec_version has to be specified");
            let witnesses = unwrap_or_return!(witnesses, env, "witnesses has to be specified");
           
            let xt: UncheckedExtrinsicV3<_, sr25519::Pair> = compose_extrinsic_offline!(
                pair,
                Call::EncointerCeremonies(EncointerCeremoniesCall::register_witnesses(witnesses.clone())),
                nonce,
                genesis_hash,
                spec_version
            );
            json_req::author_submit_and_watch_extrinsic_with_id(&xt.hex_encode(),id)
        },
        _ => {
            error!("getJsonReq: unknown request: {}", request);
            serde_json::Value::default()
        },
    };
    info!("getJsonReq returning: {:?}",jsonreq);
    let output = env.new_string(jsonreq.to_string()).unwrap();
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
