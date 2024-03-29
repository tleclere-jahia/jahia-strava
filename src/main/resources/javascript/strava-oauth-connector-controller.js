(function () {
    'use strict';

    angular.module('JahiaOAuthApp').controller('StravaOAuthConnectorController', StravaOAuthConnectorController);
    StravaOAuthConnectorController.$inject = ['$location', 'settingsService', 'helperService', 'i18nService'];

    function StravaOAuthConnectorController($location, settingsService, helperService, i18nService) {
        // must mach value in the plugin in pom.xml
        i18nService.addKey(jahiastravai18n);

        const vm = this;

        vm.connectorServiceName = 'StravaApi20';
        vm.properties = [
            {name: 'enabled', mandatory: true},
            {name: 'apiKey', mandatory: true},
            {name: 'apiSecret', mandatory: true},
            {name: 'scope', mandatory: true},
            {name: 'callbackUrl', mandatory: true}
        ];

        vm.saveSettings = () => {
            try {
                angular.forEach(vm.properties, property => {
                    if (property.mandatory && property.name && vm[property.name] === undefined) {
                        throw new Error(i18nService.message('label.missingMandatoryProperties'));
                    }
                })
            } catch (e) {
                helperService.errorToast(e.message);
                return false;
            }

            // the node name here must be the same as the one in your spring file
            const properties = {};
            angular.forEach(vm.properties, property => {
                if (vm[property.name] !== undefined) {
                    properties[property.name] = vm[property.name];
                }
            })
            settingsService.setConnectorData({connectorServiceName: vm.connectorServiceName, properties})
                .success(() => {
                    vm.connectorHasSettings = true;
                    helperService.successToast(i18nService.message('label.saveSuccess'));
                }).error(data => helperService.errorToast(data.error));
        };

        vm.goToMappers = () => $location.path(`/mappers/${vm.connectorServiceName}`);

        vm.toggleCard = () => vm.expandedCard = !vm.expandedCard;

        settingsService.getConnectorData(vm.connectorServiceName, vm.properties.map(item => item.name))
            .success(data => {
                if (data && !angular.equals(data, {})) {
                    vm.connectorHasSettings = true;
                    vm.enabled = data.enabled;
                    angular.forEach(vm.properties, property => vm[property.name] = data[property.name] || property.defaultValue);
                    vm.expandedCard = true;
                } else {
                    angular.forEach(vm.properties, property => vm[property.name] = property.defaultValue || null);
                    vm.connectorHasSettings = false;
                    vm.enabled = false;
                }
            }).error(data => helperService.errorToast(data.error));
    }
})();
