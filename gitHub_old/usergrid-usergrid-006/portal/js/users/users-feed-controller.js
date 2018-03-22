/**
 Licensed to the Apache Software Foundation (ASF) under one
 or more contributor license agreements.  See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership.  The ASF licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.
 */

'use strict'
AppServices.Controllers.controller('UsersFeedCtrl', ['ug', '$scope', '$rootScope', '$location',
  function (ug, $scope, $rootScope, $location) {

    $scope.activitiesSelected = 'active';
    $scope.activityToAdd = '';
    $scope.activities = [];
    $scope.newActivity = {};
    var getFeed = function(){
      ug.getEntityActivities($rootScope.selectedUser,true);
    };

    if (!$rootScope.selectedUser) {
      $location.path('/users');
      return;
    } else {
      getFeed();
    }

    $scope.$on('users-feed-error',function(){
      $rootScope.$broadcast('alert', 'error', 'could not create activity');
    });
    $scope.$on('users-feed-received',function(evt,entities){
      $scope.activities = entities;
      $scope.applyScope();
    });

  }]);
