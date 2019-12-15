package com.gruppo1.distributednetworkmanager.pendingrequests;

import androidx.annotation.NonNull;

import com.gruppo1.distributednetworkmanager.ActionPropagator;
import com.gruppo1.distributednetworkmanager.KadAction;
import com.gruppo1.distributednetworkmanager.NodeDataProvider;
import com.gruppo1.distributednetworkmanager.exceptions.InvalidActionException;
import com.gruppo1.distributednetworkmanager.listeners.PingResultListener;

class PingPendingRequest implements PendingRequest{

    private static final String CON_ERR = "Tried to initialize Ping Request with a different Request Type: ";

    private int stepsTaken = 0;
    private KadAction pingAction;
    private ActionPropagator actionPropagator;
    private NodeDataProvider nodeProvider;
    private PingResultListener resultListener;

    /**
     * Default constructor
     * @param action the Action from which to build this PendingRequest. Must not be null
     * @param actionPropagator
     * @param nodeProvider
     * @param resultListener the listener to this Request's Result. Must not be null.
     * @throws InvalidActionException if the Action is not a Ping Action
     */
    public PingPendingRequest(
            @NonNull KadAction action,
            @NonNull ActionPropagator actionPropagator,
            @NonNull NodeDataProvider nodeProvider,
            @NonNull PingResultListener resultListener
    ) throws InvalidActionException {
        if(action.getActionType() != KadAction.ActionType.PING)
            throw new InvalidActionException(CON_ERR+action.getActionType());
        this.pingAction = action;
        this.actionPropagator = actionPropagator;
        this.nodeProvider = nodeProvider;
        this.resultListener = resultListener;
    }

    /**
     * @return the number of steps performed (number of times nextStep took a valid Action and acted
     * accordingly). Should be either 0 or 1.
     */
    @Override
    public int getStepsTaken(){
        return stepsTaken;
    }

    /**
     * @return the unique Code for this PendingRequest
     */
    @Override
    public int getOperationId() {
        return pingAction.getOperationId();
    }

    /**
     * @return the Action that started this PingPendingRequest.
     */
    @Override
    public KadAction getStartingAction(){
        return pingAction;
    }

    /**
     * Method used to start the PendingRequest, propagating its first Action
     */
    @Override
    public void start(){
        actionPropagator.propagateAction(pingAction);
    }

    /**
     * @return true if the given action can be used to continue the Request, false otherwise. The action
     * can be used if Type and ID match, and if this request hasn't been canceled yet.
     */
    @Override
    public boolean isActionPertinent(KadAction action){
        //The Request can only continue if it receives an answer to the ping
        return KadAction.ActionType.PING_ANSWER == action.getActionType() &&
                pingAction.getOperationId() == action.getOperationId();
    }

    /**
     * Method to perform the next step for this PendingRequest. Receiving a response is already
     *
     * @param action the Action triggering the step
     */
    @Override
    public void nextStep(KadAction action) {
        if(!isActionPertinent(action)) return;
        //The Ping, one way or another, came back, so the result is positive and no further Actions are needed
        resultListener.onPingResult(getOperationId(), action.getPeer(), true);
        stepsTaken++;
    }
}
