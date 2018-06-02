'use strict';

angular.module('cosyan')
.controller('EntityCtrl', function($scope, $http) {
  $scope.load = function() {
    $http.get("/cosyan/entityMeta", {
      params: { sql: $scope.query }
    }).then(function success(response) {
      $scope.data = response.data;
      $scope.$error = undefined;
      $scope.loadedEntity = undefined;
      $scope.entityList = undefined;
    }, function error(response) {
      $scope.meta = undefined;
      $scope.$error = response.data.error;
      $scope.loadedEntity = undefined;
      $scope.entityList = undefined;
    });
  };
  
  $scope.switchEntity = function(name) {
    for (var i = 0; i < $scope.data.entities.length; i++) {
      if ($scope.data.entities[i].name == name) {
        $scope.activeEntity = $scope.data.entities[i];
      }
    }
  };
  
  $scope.searchFields = {};
  $scope.searchEntity = function() {
    if (!$scope.activeEntity) {
      return;
    }
    var params = { table: $scope.activeEntity.name };
    for (var field in $scope.searchFields) {
      params['filter_' + field] = $scope.searchFields[field];
    }
    $http.get("/cosyan/searchEntity", {
      params: params
    }).then(function success(response) {
      $scope.entityList = response.data.result[0];
      $scope.$error = undefined;
      $scope.loadedEntity = undefined;
    }, function error(response) {
      $scope.entityList = undefined;
      $scope.$error = response.data.error;
      $scope.loadedEntity = undefined;
    });
  };
  
  $scope.openEntity = function(table, id) {
    $http.get("/cosyan/loadEntity", {
      params: { table: table, id: id }
    }).then(function success(response) {
      $scope.loadedEntity = response.data.result[0];
      $scope.$error = undefined;
    }, function error(response) {
      $scope.loadedEntity = undefined;
      $scope.$error = response.data.error;
    });
  };
  
  $scope.newEntity = function() {
    if (!$scope.activeEntity) {
      return;
    }
    var entity = { type: $scope.activeEntity.name, fields: [] };
    for (var i in $scope.activeEntity.fields) {
      var field = $scope.activeEntity.fields[i];
      entity.fields.push({ name: field.name, type: field.type, value: undefined });
    }
    $scope.loadedEntity = entity;
    $scope.$error = undefined;
  };
  
  $scope.deleteEntity = function(table, id) {
    $http.get("/cosyan/deleteEntity", {
      params: { table: table, id: id }
    }).then(function reload(response) {
      $scope.searchEntity();
    });
  };
  
  $scope.load();
});