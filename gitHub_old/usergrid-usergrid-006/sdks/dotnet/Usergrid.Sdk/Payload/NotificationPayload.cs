// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

using System;
using System.Collections.Generic;
using System.Dynamic;
using RestSharp;
using Newtonsoft.Json;

namespace Usergrid.Sdk.Payload
{
    internal class NotificationPayload
    {
        
        public IDictionary<string, object>  Payloads { get; set; }
		[JsonProperty("deliver", NullValueHandling = NullValueHandling.Ignore)]
		public long? DeliverAt {get;set;}
		[JsonProperty("expire", NullValueHandling = NullValueHandling.Ignore)]
		public long? ExpireAt { get; set;}

        public NotificationPayload()
        {
            Payloads = new Dictionary<string, object>();
        }
    }
}
