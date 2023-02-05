package com.vpulse.ftpnext.database.TableTest1

import com.vpulse.ftpnext.database.ABaseTable

class TableTest1 : ABaseTable {
    var value: Int = 0

    constructor() {}
    constructor(iValue: Int) {
        value = iValue
    }

    public override fun toString(): String {
        return "test"
    }
}