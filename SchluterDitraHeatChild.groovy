/*
* SchluterDitraHeatChild
*
* Description:
* This Hubitat driver is designed for use with a Schluter DITRA-HEAT Wifi thermostat.  This is the child device per thermostat.  It takes no direct configuration
*
* Preferences:
*
* Features List:
*   Control Schluter DITRA-HEAT Wifi thermostats
* 
* Licensing:
* Copyright 2023 Marc Reyhner
* Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
* in compliance with the License. You may obtain a copy of the License at: http://www.apache.org/licenses/LICENSE-2.0
* Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
* on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
* for the specific language governing permissions and limitations under the License.
*
* Version Control:
* 0.1.0 - Initial version
* 
* Thank you(s):
*   I used the Tesla PowerWall driver fron snell as a sample and skeleton for creating a HTTP based driver
*       https://community.hubitat.com/t/project-driver-for-connecting-to-your-tesla-devices/41375
*   API details learned from py-schluter library - https://github.com/prairieapps/py-schluter/
*/

import groovy.json.*

metadata{
    definition ( name: "SchluterDitraHeatChild", namespace: "marcre", author: "Marc Reyhner", importUrl: "tbd" ) {
        capability "Sensor"
        capability "Refresh"
        capability "TemperatureMeasurement"
        capability "Thermostat"
        capability "ThermostatHeatingSetpoint"
        capability "ThermostatOperatingState"
        capability "ThermostatSetpoint"
        attribute "Group Name", "string"
        attribute "Manual Temperature", "number"
        attribute "Maximum Temperature", "number"
        attribute "Minimum Temperature", "number"
        attribute "Measured Load", "number"
        attribute "Schedule Mode", "string"
        attribute "Software Version", "string"

        // Temperature Measurement
        attribute "temperature", "number"

        // Thermostat
        attribute "heatingSetpoint", "number"
        attribute "supportedThermostatFanModes", "string"
        attribute "supportedThermostatModes", "string"
        attribute "thermostatFanMode", "string"
        attribute "thermostatMode", "string"
        attribute "thermostatOperatingState", "string"
        attribute "thermostatSetpoint", "number"
    }
}

def installed() {
    // Set static attributes at install time
    sendEvent(name: 'supportedThermostatFanModes', value: JsonOutput.toJson(["off"]) )
    sendEvent(name: 'supportedThermostatModes', value: JsonOutput.toJson(["heat", "off"]) )
    sendEvent(name: "thermostatFanMode", value: "off")
}

def ProcessUpdate(thermostat) {
    if (getParent().DebugLogsEnabled()) log.debug("Thermostat raw data ${thermostat}.toString()")

    UpsertAttribute("temperature", thermostatToSystemUnits(thermostat.Temperature, 1), location.temperatureScale)
    UpsertAttribute("thermostatOperatingState", thermostat.Heating ? "heating" : "idle")
    UpsertAttribute("thermostatSetpoint", (int)thermostatToSystemUnits(thermostat.SetPointTemp, 0), location.temperatureScale)
    UpsertAttribute("heatingSetpoint", (int)thermostatToSystemUnits(thermostat.SetPointTemp, 0), location.temperatureScale)
    UpsertAttribute("Group Name", thermostat.GroupName)
    UpsertAttribute("Manual Temperature", (int)thermostatToSystemUnits(thermostat.ManualTemperature, 0), location.temperatureScale)
    UpsertAttribute("Maximum Temperature", (int)thermostatToSystemUnits(thermostat.MaxTemp, 0), location.temperatureScale)
    UpsertAttribute("Minimum Temperature", (int)thermostatToSystemUnits(thermostat.MinTemp, 0), location.temperatureScale)
    UpsertAttribute("Measured Load", thermostat.LoadMeasuredWatt, "W")
    UpsertAttribute("Schedule Mode", ResolveScheduleMode(thermostat.RegulationMode))
    UpsertAttribute("thermostatMode", thermostat.RegulationMode == 4 ? "off" : "heat")
    UpsertAttribute("Software Version", thermostat.SWVersion)
}

def cool() {
    log.warn("cool() is not supported and takes no action.")
}

def emergencyHeat() {
    log.warn("emergencyHeat() is not supported and takes no action.")
}

def setCoolingSetpoint(requestedTemperator) {
    log.warn("setCoolingSetpoint() is not supported and takes no action.")
}

def fanAuto() {
    log.warn("fanAuto() is not supported and takes no action.")
}

def fanCirculate() {
    log.warn("fanCirculate() is not supported and takes no action.")
}

def fanOn() {
    log.warn("fanOn() is not supported and takes no action.")
}

def setSchedule(schedule) {
    log.warn("setSchedule() is not supported and takes no action.")
}

def setThermostatFanMode(fanmode) {
    log.warn("setThermostatFanMode() is not supported and takes no action.")
}

def auto() {
    log.info("auto() on ${device.getDisplayName()} invoked.")
    getParent().SetThermostatFollowSchedule(device.deviceNetworkId)
}

def heat() {
    log.info("auto() on ${device.getDisplayName()} invoked.")
    getParent().SetThermostatFollowSchedule(device.deviceNetworkId)
}

def off() {
    log.info("off() on ${device.getDisplayName()} invoked.")
    getParent().SetThermostatVacationMode(device.deviceNetworkId)
}

def refresh() {
    log.info("refresh() on ${device.getDisplayName()} invoked.")
    getParent().refresh()
}

def setThermostatMode(thermostatMode) {
    log.warn("setThermostatMode() is not supported and takes no action.")
}

def setHeatingSetpoint(temperature) {
    log.info("setHeatingSetpoint(${temperature}) on ${device.getDisplayName()} invoked.")
    
    minimumTemperature = device.currentValue("Minimum Temperature")
    
    if (temperature < minimumTemperature) {
        log.warn("setHeatingSetpoint(${temperature}) on ${device.getDisplayName()} is less than minimum temperature ${minimumTemperature}.  Will set to minimum temperature.")
        
        temperature = minimumTemperature;
    }
    
    maximumTemperature = device.currentValue("Maximum Temperature")
    
    if (temperature > maximumTemperature) {
        log.warn("setHeatingSetpoint(${temperature}) on ${device.getDisplayName()} is greater than maximum temperature ${maximumTemperature}.  Will set to maximum temperature.")
        
        temperature = maximumTemperature;
    }
    
    getParent().SetThermostatTemperature(device.deviceNetworkId, (int)(systemUnitsToCelsius(temperature)*100))
}


def UpsertAttribute( Variable, Value, Unit = null ){
    if( device.currentValue(Variable) != Value ){

        if( Unit != null ){
            log.info( "Event: ${ Variable } = ${ Value }${ Unit }" )
            sendEvent( name: "${ Variable }", value: Value, unit: Unit )
        } else {
            log.info( "Event: ${ Variable } = ${ Value }" )
            sendEvent( name: "${ Variable }", value: Value )
        }
    }
}

// Convert thermostat temperature (celsius * 100) to the system temperature
def thermostatToSystemUnits(thermostatTemperature, precision) {
    return celsiusToSystemUnits(thermostatTemperature / 100).doubleValue().round(precision)
}

// Convert celsius to the system temperature
def celsiusToSystemUnits(celsiusTemperature) {
    return (location.temperatureScale == "F") ? celsiusToFahrenheit(celsiusTemperature) : celsiusTemperature
}

// Convert system units to celsius
def systemUnitsToCelsius(temperature) {
    return (location.temperatureScale == "F") ? fahrenheitToCelsius(temperature) : temperature
}

// Translate the schedule mode enumeration from the thermostat to a friendly string
def ResolveScheduleMode(regulationMode) {
    switch (regulationMode) {
        case 1:
            return "Follow Schedule"
        case 2:
            return "Temporary Hold"
        case 3:
            return "Permanently Hold"
        case 4:
            return "Vacation"
        default:
            return regulationMode.toString()
    }
}
