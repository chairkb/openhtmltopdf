/*
 * {{{ header & license
 * Copyright (c) 2004, 2005 Who?
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * }}}
 */
package com.openhtmltopdf.resource;

import com.openhtmltopdf.extend.FSImage;

public class ImageResource {
    private final String _imageUri;
    private final FSImage _img;

    public ImageResource(final String uri, FSImage img) {
        _imageUri = uri;
        _img = img;
    }

    public FSImage getImage() {
        return _img;
    }

    public boolean isLoaded() {
        return true;
    }

    public String getImageUri() {
        return _imageUri;
    }
}