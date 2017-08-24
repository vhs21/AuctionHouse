package com.epam.auction.command.item;

import com.epam.auction.command.AbstractCommand;
import com.epam.auction.command.RequestContent;
import com.epam.auction.controller.PageAddress;
import com.epam.auction.controller.PageGuide;
import com.epam.auction.controller.TransferMethod;
import com.epam.auction.exception.ReceiverLayerException;
import com.epam.auction.receiver.Receiver;
import com.epam.auction.receiver.RequestConstant;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LoadItemCommand extends AbstractCommand {

    private static final Logger LOGGER = LogManager.getLogger();

    public LoadItemCommand(Receiver receiver) {
        super(receiver);
    }

    @Override
    public PageGuide execute(RequestContent requestContent) {

        if (requestContent.getSessionAttribute(RequestConstant.CURRENT_PAGE) != PageAddress.ITEM) {
            requestContent.removeSessionAttribute(RequestConstant.MESSAGE);
        }

        PageGuide pageGuide = new PageGuide(PageAddress.ITEM, TransferMethod.FORWARD);

        try {
            doAction(requestContent);
        } catch (ReceiverLayerException e) {
            LOGGER.log(Level.ERROR, e.getMessage(), e);
        }

        return pageGuide;
    }
}