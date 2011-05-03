/*
 * Copyright 2002-2011 Drew Noakes
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 * More information about this project is available at:
 *
 *    http://drewnoakes.com/code/exif/
 *    http://code.google.com/p/metadata-extractor/
 */

package com.drew.metadata.icc;

import com.drew.lang.BufferBoundsException;
import com.drew.lang.BufferReader;
import com.drew.lang.annotations.NotNull;
import com.drew.lang.annotations.Nullable;
import com.drew.metadata.TagDescriptor;

import java.io.UnsupportedEncodingException;

/** @author Yuri Binev, Drew Noakes http://drewnoakes.com */
public class IccDescriptor extends TagDescriptor
{
    public IccDescriptor(@NotNull IccDirectory directory)
    {
        super(directory);
    }

    public String getDescription(int tagType)
    {
        switch (tagType) {
            case IccDirectory.TAG_ICC_PROFILE_BYTES:
                return getProfileBytesDescription();
            case IccDirectory.TAG_ICC_PROFILE_VERSION:
                return getProfileVersionDescription();
            case IccDirectory.TAG_ICC_PROFILE_CLASS:
                return getProfileClassDescription();
            case IccDirectory.TAG_ICC_PLATFORM:
                return getPlatformDescription();
            case IccDirectory.TAG_ICC_RENDERING_INTENT:
                return getRenderingIntentDescription();
        }

        if (tagType > 0x20202020 && tagType < 0x7a7a7a7a)
            return getTagDataString(tagType);

        return _directory.getString(tagType);
    }

    private static final int ICC_TAG_TYPE_TEXT = 0x74657874;
    private static final int ICC_TAG_TYPE_DESC = 0x64657363;
    private static final int ICC_TAG_TYPE_SIG = 0x73696720;
    private static final int ICC_TAG_TYPE_MEAS = 0x6D656173;
    private static final int ICC_TAG_TYPE_XYZ_ARRAY = 0x58595A20;
    private static final int ICC_TAG_TYPE_MLUC = 0x6d6c7563;
    private static final int ICC_TAG_TYPE_CURV = 0x63757276;

    @Nullable
    private String getTagDataString(int tagType)
    {
        try {
            byte[] bytes = _directory.getByteArray(tagType);
            BufferReader reader = new BufferReader(bytes);
            int iccTagType = reader.getInt32(0);
            switch (iccTagType) {
                case ICC_TAG_TYPE_TEXT:
                    try {
                        return new String(bytes, 8, bytes.length - 8 - 1, "ASCII");
                    } catch (UnsupportedEncodingException ex) {
                        return new String(bytes, 8, bytes.length - 8 - 1);
                    }
                case ICC_TAG_TYPE_DESC:
                    int stringLength = reader.getInt32(8);
                    return new String(bytes, 12, stringLength - 1);
                case ICC_TAG_TYPE_SIG:
                    return IccReader.getStringFromInt32(reader.getInt32(8));
                case ICC_TAG_TYPE_MEAS: {
                    int observerType = reader.getInt32(8);
                    float x = reader.getS15Fixed16(12);
                    float y = reader.getS15Fixed16(16);
                    float z = reader.getS15Fixed16(20);
                    int geometryType = reader.getInt32(24);
                    float flare = reader.getS15Fixed16(28);
                    int illuminantType = reader.getInt32(32);
                    String observerString;
                    switch (observerType) {
                        case 0:
                            observerString = "Unknown";
                            break;
                        case 1:
                            observerString = "1931 2°";
                            break;
                        case 2:
                            observerString = "1964 10°";
                            break;
                        default:
                            observerString = String.format("Unknown %d", observerType);
                    }
                    String geometryString;
                    switch (geometryType) {
                        case 0:
                            geometryString = "Unknown";
                            break;
                        case 1:
                            geometryString = "0/45 or 45/0";
                            break;
                        case 2:
                            geometryString = "0/d or d/0";
                            break;
                        default:
                            geometryString = String.format("Unknown %d", observerType);
                    }
                    String illuminantString;
                    switch (illuminantType) {
                        case 0:
                            illuminantString = "unknown";
                            break;
                        case 1:
                            illuminantString = "D50";
                            break;
                        case 2:
                            illuminantString = "D65";
                            break;
                        case 3:
                            illuminantString = "D93";
                            break;
                        case 4:
                            illuminantString = "F2";
                            break;
                        case 5:
                            illuminantString = "D55";
                            break;
                        case 6:
                            illuminantString = "A";
                            break;
                        case 7:
                            illuminantString = "Equi-Power (E)";
                            break;
                        case 8:
                            illuminantString = "F8";
                            break;
                        default:
                            illuminantString = String.format("Unknown %d", illuminantType);
                            break;
                    }
                    return String.format("%s Observer, Backing (%s, %s, %s), Geometry %s, Flare %d%%, Illuminant %s",
                            observerString, x, y, z, geometryString, Math.round(flare * 100), illuminantString);
                }
                case ICC_TAG_TYPE_XYZ_ARRAY: {
                    StringBuilder res = new StringBuilder();
                    int count = (bytes.length - 8) / 12;
                    for (int i = 0; i < count; i++) {
                        float x = reader.getS15Fixed16(8 + i * 12);
                        float y = reader.getS15Fixed16(8 + i * 12 + 4);
                        float z = reader.getS15Fixed16(8 + i * 12 + 8);
                        if (i > 0)
                            res.append(", ");
                        res.append("(").append(x).append(", ").append(y).append(", ").append(z).append(")");
                    }
                    return res.toString();
                }
                case ICC_TAG_TYPE_MLUC: {
                    int int1 = reader.getInt32(8);
                    StringBuilder res = new StringBuilder();
                    res.append(int1);
                    //int int2 = reader.getInt32(12);
                    //System.err.format("int1: %d, int2: %d\n", int1, int2);
                    for (int i = 0; i < int1; i++) {
                        String str = IccReader.getStringFromInt32(reader.getInt32(16 + i * 12));
                        int len = reader.getInt32(16 + i * 12 + 4);
                        int ofs = reader.getInt32(16 + i * 12 + 8);
                        String name;
                        try {
                            name = new String(bytes, ofs, len, "UTF-16BE");
                        } catch (UnsupportedEncodingException ex) {
                            name = new String(bytes, ofs, len);
                        }
                        res.append(" ").append(str).append("(").append(name).append(")");
                        //System.err.format("% 3d: %s, len: %d, ofs: %d, \"%s\"\n", i, str, len,ofs,name);
                    }
                    return res.toString();
                }
                case ICC_TAG_TYPE_CURV: {
                    int num = reader.getInt32(8);
                    StringBuilder res = new StringBuilder();
                    for (int i = 0; i < num; i++) {
                        if (i != 0)
                            res.append(", ");
                        res.append(formatDoubleAsString(((float)reader.getUInt16(12 + i * 2)) / 65535.0, 7, false));
                        //res+=String.format("%1.7g",Math.round(((float)iccReader.getInt16(b,12+i*2))/0.065535)/1E7);
                    }
                    return res.toString();
                }
                default:
                    return String.format("%s(0x%08X): %d bytes", IccReader.getStringFromInt32(iccTagType), iccTagType, bytes.length);
            }
        } catch (BufferBoundsException e) {
            // TODO decode these values during IccReader.extract so we can report any errors at that time
            // It is convention to return null if a description cannot be formulated.
            // If an error is to be reported, it should be done during the extraction process.
            return null;
        }
    }

    @NotNull
    public static String formatDoubleAsString(double value, int precision, boolean zeroes)
    {
        if (precision < 1)
            return "" + Math.round(value);
        long intPart = Math.abs((long)value);
        long rest = (int)Math.round((Math.abs(value) - intPart) * Math.pow(10, precision));
        long restKept = rest;
        String res = "";
        byte cour;
        for (int i = precision; i > 0; i--) {
            cour = (byte)(Math.abs(rest % 10));
            rest /= 10;
            if (res.length() > 0 || zeroes || cour != 0 || i == 1)
                res = cour + res;
        }
        intPart += rest;
        boolean isNegative = ((value < 0) && (intPart != 0 || restKept != 0));
        return (isNegative ? "-" : "") + intPart + "." + res;
    }

    @Nullable
    private String getRenderingIntentDescription()
    {
        Integer value = _directory.getInteger(IccDirectory.TAG_ICC_RENDERING_INTENT);

        if (value == null)
            return null;

        switch (value) {
            case 0:
                return "Perceptual";
            case 1:
                return "Media-Relative Colorimetric";
            case 2:
                return "Saturation";
            case 3:
                return "ICC-Absolute Colorimetric";
            default:
                return String.format("Unknown (%d)", value);
        }
    }

    @Nullable
    private String getPlatformDescription()
    {
        String str = _directory.getString(IccDirectory.TAG_ICC_PLATFORM);
        if (str==null)
            return null;
        // Because Java doesn't allow switching on string values, create an integer from the first four chars
        // and switch on that instead.
        int i;
        try {
            i = getInt32FromString(str);
        } catch (BufferBoundsException e) {
            return str;
        }
        switch (i) {
            case 0x4150504C: // "APPL"
                return "Apple Computer, Inc.";
            case 0x4D534654: // "MSFT"
                return "Microsoft Corporation";
            case 0x53474920:
                return "Silicon Graphics, Inc.";
            case 0x53554E57:
                return "Sun Microsystems, Inc.";
            case 0x54474E54:
                return "Taligent, Inc.";
            default:
                return String.format("Unknown (%s)", str);
        }
    }

    @Nullable
    private String getProfileClassDescription()
    {
        String str = _directory.getString(IccDirectory.TAG_ICC_PROFILE_CLASS);
        if (str==null)
            return null;
        // Because Java doesn't allow switching on string values, create an integer from the first four chars
        // and switch on that instead.
        int i;
        try {
            i = getInt32FromString(str);
        } catch (BufferBoundsException e) {
            return str;
        }
        switch (i) {
            case 0x73636E72:
                return "Input Device";
            case 0x6D6E7472: // mntr
                return "Display Device";
            case 0x70727472:
                return "Output Device";
            case 0x6C696E6B:
                return "DeviceLink";
            case 0x73706163:
                return "ColorSpace Conversion";
            case 0x61627374:
                return "Abstract";
            case 0x6E6D636C:
                return "Named Color";
            default:
                return String.format("Unknown (%s)", str);
        }
    }

    @Nullable
    private String getProfileVersionDescription()
    {
        Integer value = _directory.getInteger(IccDirectory.TAG_ICC_PROFILE_VERSION);

        if (value == null)
            return null;

        int m = (value & 0xFF000000) >> 24;
        int r = (value & 0x00F00000) >> 20;
        int R = (value & 0x000F0000) >> 16;

        return String.format("%d.%d.%d", m, r, R);
    }

    @Nullable
    public String getProfileBytesDescription()
    {
        final byte[] bytes = _directory.getByteArray(IccDirectory.TAG_ICC_PROFILE_BYTES);
        if (bytes == null)
            return null;
        return String.format("%d bytes binary data", bytes.length);
    }

    private static int getInt32FromString(@NotNull String string) throws BufferBoundsException
    {
        byte[] bytes = string.getBytes();
        return new BufferReader(bytes).getInt32(0);
    }
}
