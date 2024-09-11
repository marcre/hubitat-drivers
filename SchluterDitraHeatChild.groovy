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
import java.time.LocalDate

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

        command "followSchedule"
        command "vacationMode", [
            [name: "Start Date (inclusive)", type: "STRING", description: "YYYY-MM-DD"],
            [name: "End Date (exclusive)", type: "STRING", description: "YYYY-MM-DD"],
            [name: "Temperature", type: "NUMBER", unit: "°"]
        ]
    }
}

def installed() {
    // Set static attributes at install time
    sendEvent(name: 'supportedThermostatFanModes', value: JsonOutput.toJson(["off"]) )
    sendEvent(name: 'supportedThermostatModes', value: JsonOutput.toJson(["heat"]) )
    sendEvent(name: "thermostatFanMode", value: "off")
    sendEvent(name: "thermostatMode", value: "heat")
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
    log.warn("auto() is not supported and takes no action. Consider setting the thermostat to Follow Schedule instead.")
}

def heat() {
    log.warn("heat() is not supported and takes no action. Consider setting the thermostat to Follow Schedule instead.")
}

def off() {
    log.warn("off() is not supported and takes no action. Consider setting the thermostat to Vacation Mode instead.")
}

def vacationMode(startDateString = null, endDateString = null, temperature = null) {
    log.info("vacationMode(${startDateString}, ${endDateString}, ${temperature}) on ${device.getDisplayName()} invoked.")

    LocalDate today = LocalDate.now()
    LocalDate startDate = today
    LocalDate endDate = today.plusDays(1)
    try {
        if( startDateString ) {
            startDate = LocalDate.parse(startDateString)
        }
        if( endDateString ) {
            endDate = LocalDate.parse(endDateString)
        }
    }
    catch (Exception e) {
        log.error("Error parsing dates: ${e}")
        return
    }

    // If the start date is in the past, set to today
    if (startDate < today) {
        startDate = today
    }

    // If the end date is not after the start date, set to the next day
    if (endDate <= startDate) {
        endDate = startDate.plusDays(1)
    }

    // If the temperature is outside the thermostat's range, set to the min/max
    temperature = normalizeTemperature(temperature)

    getParent().SetThermostatVacationMode(device.deviceNetworkId, startDate, endDate, (int)(systemUnitsToCelsius(temperature)*100))
}

def followSchedule() {
    log.info("followSchedule() on ${device.getDisplayName()} invoked.")
    getParent().SetThermostatFollowSchedule(device.deviceNetworkId)
}

def refresh() {
    log.info("refresh() on ${device.getDisplayName()} invoked.")
    getParent().refresh()
}

def setThermostatMode(thermostatMode) {
    log.warn("setThermostatMode() is not supported and takes no action.")
}

def normalizeTemperature(temperature) {
    minimumTemperature = device.currentValue("Minimum Temperature")

    if (temperature == null) {
        return minimumTemperature;
    }

    if (temperature < minimumTemperature) {
        log.warn("${temperature} on ${device.getDisplayName()} is less than minimum temperature ${minimumTemperature}.  Will set to minimum temperature.")

        return minimumTemperature;
    }

    maximumTemperature = device.currentValue("Maximum Temperature")

    if (temperature > maximumTemperature) {
        log.warn("setHeatingSetpoint(${temperature}) on ${device.getDisplayName()} is greater than maximum temperature ${maximumTemperature}.  Will set to maximum temperature.")

        return maximumTemperature;
    }

    return temperature;
}

def setHeatingSetpoint(temperature) {
    log.info("setHeatingSetpoint(${temperature}) on ${device.getDisplayName()} invoked.")

    temperature = normalizeTemperature(temperature)

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
