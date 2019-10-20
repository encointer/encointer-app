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
    extrinsic::xt_primitives::{AccountId, UncheckedExtrinsicV3},
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

use encointer_node_runtime::{Call, EncointerCeremoniesCall, BalancesCall, 
    Signature, Hash,
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
    android_logger::init_once(
        Config::default()
            .with_min_level(Level::Trace) // limit log level
            .with_tag("encointer-api-native")); // logs will show under mytag tag

    let arg = json::parse(CStr::from_ptr(
        env.get_string(j_arg).unwrap().as_ptr()).to_str().unwrap()).unwrap();

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
    android_logger::init_once(
        Config::default()
            .with_min_level(Level::Trace) // limit log level
            .with_tag("encointer-api-native")); // logs will show under mytag tag

    let arg = json::parse(CStr::from_ptr(
        env.get_string(j_arg).unwrap().as_ptr()).to_str().unwrap()).unwrap();

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
    android_logger::init_once(
        Config::default()
            .with_min_level(Level::Trace) // limit log level
            .with_tag("encointer-api-native")); // logs will show under mytag tag

    let request = CStr::from_ptr(
        env.get_string(j_request).unwrap().as_ptr()).to_str().unwrap();

    let arg = json::parse(CStr::from_ptr(
        env.get_string(j_arg).unwrap().as_ptr()).to_str().unwrap()).unwrap();

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
    let witnesses: Option<Vec<Witness<Signature, AccountId>>> = match &arg["witnesses"] {
        json::JsonValue::Array(w) => Some(w.iter().map(|x| 
            Decode::decode(&mut &hex::decode((*x).as_str().unwrap()).unwrap()[..])
            .unwrap()).collect()),
        _ => None,
    };
    let jsonreq = match request {
        "subscribe_events" => {
            let key = storage_key_hash("System", "Events", None);
            json_req::state_subscribe_storage(&key)
        }
        "subscribe_balance_for" => {
            let pair = pair.expect("pair is specified");
            let key = storage_key_hash("Balances", "FreeBalances", Some(AccountId::from(pair.public()).encode()));
            json_req::state_subscribe_storage(&key)
        }
        "subscribe_nonce_for" => {
            let pair = pair.expect("pair is specified");
            let key = storage_key_hash("System", "AccountNone", Some(AccountId::from(pair.public()).encode()));
            json_req::state_subscribe_storage(&key)
        },
        "get_meetup_index_for" => {
            let pair = pair.expect("pair is specified");
            // FIXME: need to implement double_map for api-client first.
            let key = storage_key_hash("EncointerCeremonies", "MeetupIndex", Some(AccountId::from(pair.public()).encode()));
            json_req::state_get_storage(&key)
        },
        "get_ceremony_index" => {
            let key = storage_key_hash("EncointerCeremonies", "CurrentCeremonyIndex", None);
            json_req::state_get_storage(&key)
        },
        "get_runtime_version" => {
            json_req::state_get_runtime_version()
        },
        "get_genesis_hash" => {
            json_req::chain_get_block_hash()
        },
        "bootstrap_funding" => {
            // TODO to be replaced by faucet
            let pair = pair.expect("pair is specified");
            let nonce = nonce.expect("nonce is specified");
            let genesis_hash = genesis_hash.expect("genesis hash is specified");
            let spec_version = spec_version.expect("spec version is specified");
            let from = AccountKeyring::Alice.pair();
            let to = AccountId::from(pair.public());
            let xt: UncheckedExtrinsicV3<_, sr25519::Pair> = compose_extrinsic_offline!(
                from,
                Call::Balances(BalancesCall::transfer(to.clone().into(), 42)),
                nonce,
                genesis_hash,
                spec_version
            );
            json_req::author_submit_and_watch_extrinsic(&xt.hex_encode())
        },
        "register_participant" => {
            let pair = pair.expect("pair is specified");
            let nonce = nonce.expect("nonce is specified");
            let genesis_hash = genesis_hash.expect("genesis hash is specified");
            let spec_version = spec_version.expect("spec version is specified");
            let xt: UncheckedExtrinsicV3<_, sr25519::Pair> = compose_extrinsic_offline!(
                pair,
                Call::EncointerCeremonies(EncointerCeremoniesCall::register_participant()),
                nonce,
                genesis_hash,
                spec_version
            );
            json_req::author_submit_and_watch_extrinsic(&xt.hex_encode())
        },
         "register_witnesses" => {
            let pair = pair.expect("pair is specified");
            let nonce = nonce.expect("nonce is specified");
            let genesis_hash = genesis_hash.expect("genesis hash is specified");
            let spec_version = spec_version.expect("spec version is specified");
            let witnesses = witnesses.expect("witnesses are specified");
            
            let xt: UncheckedExtrinsicV3<_, sr25519::Pair> = compose_extrinsic_offline!(
                pair,
                Call::EncointerCeremonies(EncointerCeremoniesCall::register_witnesses(witnesses)),
                nonce,
                genesis_hash,
                spec_version
            );
            json_req::author_submit_and_watch_extrinsic(&xt.hex_encode())
        },
        _ => {
            panic!("unknown request");
        },
       
    };
    
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
