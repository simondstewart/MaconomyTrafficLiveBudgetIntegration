package com.sohnar.trafficlite.business.utils;

import com.sohnar.trafficlite.transfer.BaseTO;

/**
 * Created by AndreaGhetti on 07/04/2016.
 */
public class ValidationResult<TYPE extends BaseTO> {

    private String result;
    private TYPE origin;
    private Boolean canBeDeleted;

    public ValidationResult(String result, TYPE origin, Boolean canBeDeleted) {
        this.result = result;
        this.origin = origin;
        this.canBeDeleted = canBeDeleted;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public TYPE getOrigin() {
        return origin;
    }

    public void setOrigin(TYPE origin) {
        this.origin = origin;
    }

    public Boolean getCanBeDeleted() {
        return canBeDeleted;
    }

    public void setCanBeDeleted(Boolean canBeDeleted) {
        this.canBeDeleted = canBeDeleted;
    }
}
